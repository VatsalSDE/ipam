package com.motadata.ipam.service;

import com.motadata.ipam.database.DbQueries;

import com.motadata.ipam.database.DbUtil;

import com.motadata.ipam.plugin.GoPluginBridge;

import io.vertx.core.Future;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonArray;

import io.vertx.core.json.JsonObject;

import io.vertx.mysqlclient.MySQLPool;

import io.vertx.sqlclient.Row;

import io.vertx.sqlclient.Tuple;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.time.Instant;

import java.util.ArrayList;

import java.util.List;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Subnet Scanning Coordinator.
 * Implements Keyset Cursor Streaming (WHERE id > lastId LIMIT 512) to ensure
 * constant O(1) memory footprint regardless of subnet size (/24 up to /8).
 */
public class ScannerService {

    private static final Logger logger = LoggerFactory.getLogger(ScannerService.class);

    private static final int DEFAULT_CHUNK_SIZE = 512;

    private final MySQLPool mysqlPool;

    private final GoPluginBridge goPluginBridge;

    private final Vertx vertx;

    // In-memory status tracker for concurrent scans: subnetId -> status JsonObject
    private final Map<Long, JsonObject> activeScans = new ConcurrentHashMap<>();

    public ScannerService(MySQLPool mysqlPool, GoPluginBridge goPluginBridge, Vertx vertx) {

        this.mysqlPool = mysqlPool;

        this.goPluginBridge = goPluginBridge;

        this.vertx = vertx;

    }

    /**
     * Triggers a live network scan for the specified subnet using keyset streaming.
     */
    public Future<JsonObject> triggerScan(Long subnetId) {

        if (subnetId == null || subnetId <= 0) {

            return Future.failedFuture("Invalid subnet ID");

        }

        JsonObject existingScan = activeScans.get(subnetId);

        if (existingScan != null && "IN_PROGRESS".equalsIgnoreCase(existingScan.getString("status"))) {

            return Future.succeededFuture(new JsonObject()
                    .put("subnetId", subnetId)
                    .put("status", "IN_PROGRESS")
                    .put("message", "Scan is already in progress for this subnet"));

        }

        long startTime = Instant.now().toEpochMilli();

        JsonObject initialStatus = new JsonObject()
                .put("subnetId", subnetId)
                .put("status", "IN_PROGRESS")
                .put("startTime", startTime)
                .put("processedIps", 0)
                .put("chunksProcessed", 0)
                .put("message", "Subnet scan initiated with keyset streaming");

        activeScans.put(subnetId, initialStatus);

        logger.info("Initiating keyset streaming subnet scan for Subnet ID: {}", subnetId);

        String fetchSubnetSql = DbQueries.SCANNER_FETCH_SUBNET;

        String countIpsSql = DbQueries.SCANNER_COUNT_IPS;

        // Run scanning asynchronously on worker thread without blocking HTTP response
        mysqlPool.preparedQuery(fetchSubnetSql).execute(Tuple.of(subnetId))
                .compose(subnetRows -> {

                    if (!subnetRows.iterator().hasNext()) {

                        activeScans.remove(subnetId);

                        return Future.<JsonObject>failedFuture("Subnet with ID " + subnetId + " not found");

                    }

                    Row subnetRow = subnetRows.iterator().next();

                    String subnetName = subnetRow.getString("subnetName");

                    String subnetAddress = subnetRow.getString("subnetAddress");

                    Integer subnetCidr = subnetRow.getInteger("subnetCidr");

                    return mysqlPool.preparedQuery(countIpsSql).execute(Tuple.of(subnetId))
                            .compose(countRows -> {

                                long totalIps = 0;

                                if (countRows.iterator().hasNext()) {

                                    totalIps = countRows.iterator().next().getLong("total");

                                }

                                if (totalIps == 0) {

                                    JsonObject emptyResult = new JsonObject()
                                            .put("subnetId", subnetId)
                                            .put("subnetName", subnetName)
                                            .put("status", "COMPLETED")
                                            .put("totalIps", 0)
                                            .put("upCount", 0)
                                            .put("downCount", 0)
                                            .put("message", "No IP records found in subnet");

                                    activeScans.put(subnetId, emptyResult);

                                    return Future.succeededFuture(emptyResult);

                                }

                                JsonArray allUpIps = new JsonArray();

                                JsonArray allDownIps = new JsonArray();

                                // Stream chunks 512 at a time using keyset pagination
                                return streamAndScanChunks(
                                        subnetId,
                                        subnetName,
                                        subnetAddress,
                                        subnetCidr,
                                        0L,
                                        totalIps,
                                        allUpIps,
                                        allDownIps,
                                        startTime,
                                        0
                                );

                            });

                })
                .onFailure(err -> {

                    logger.error("Failed to execute scan for subnet ID {}: {}", subnetId, err.getMessage(), err);

                    JsonObject failedStatus = new JsonObject()
                            .put("subnetId", subnetId)
                            .put("status", "FAILED")
                            .put("error", err.getMessage())
                            .put("failedAt", Instant.now().toEpochMilli());

                    activeScans.put(subnetId, failedStatus);

                });

        return Future.succeededFuture(initialStatus);

    }

    /**
     * Recursively streams chunks of 512 IPs from MariaDB using Keyset Cursor (WHERE id > lastSeenId LIMIT 512).
     * Keeps JVM heap memory flat at constant O(1) size regardless of subnet size.
     */
    private Future<JsonObject> streamAndScanChunks(
            Long subnetId,
            String subnetName,
            String subnetAddress,
            Integer subnetCidr,
            long lastSeenId,
            long totalIps,
            JsonArray allUpIps,
            JsonArray allDownIps,
            long startTime,
            int chunksProcessed
    ) {

        String fetchChunkSql = DbQueries.SCANNER_FETCH_IP_CHUNK;

        return mysqlPool.preparedQuery(fetchChunkSql).execute(Tuple.of(subnetId, lastSeenId, DEFAULT_CHUNK_SIZE))
                .compose(rows -> {

                    if (!rows.iterator().hasNext()) {

                        // All chunks finished! Recalculate parent subnet counters
                        long durationMs = Instant.now().toEpochMilli() - startTime;

                        return recalculateSubnetCounters(subnetId)
                                .map(v -> {

                                    logger.info("Scan completed for Subnet {}: {} UP, {} DOWN in {} ms across {} streamed chunks",
                                            subnetName, allUpIps.size(), allDownIps.size(), durationMs, chunksProcessed);

                                    JsonObject summary = new JsonObject()
                                            .put("subnetId", subnetId)
                                            .put("subnetName", subnetName)
                                            .put("subnetAddress", subnetAddress)
                                            .put("subnetCidr", subnetCidr)
                                            .put("status", "COMPLETED")
                                            .put("totalIps", totalIps)
                                            .put("chunksProcessed", chunksProcessed)
                                            .put("upCount", allUpIps.size())
                                            .put("downCount", allDownIps.size())
                                            .put("upIps", allUpIps)
                                            .put("durationMs", durationMs)
                                            .put("completedAt", Instant.now().toEpochMilli());

                                    activeScans.put(subnetId, summary);

                                    if (vertx != null && vertx.eventBus() != null) {

                                        vertx.eventBus().publish("ipam.subnet.scan.completed", summary);

                                        // Automated Alert Trigger: If subnet utilization >= 80%, publish HIGH_UTILIZATION alert
                                        if (totalIps > 0 && ((double) allUpIps.size() / totalIps) >= 0.80) {

                                            int pct = (int) (((double) allUpIps.size() / totalIps) * 100);

                                            JsonObject alertMsg = new JsonObject()
                                                    .put("subnetId", subnetId)
                                                    .put("alertType", "HIGH_UTILIZATION")
                                                    .put("message", "Subnet '" + subnetName + "' utilization reached " + pct + "% (" + allUpIps.size() + "/" + totalIps + " IPs used)")
                                                    .put("subnet", subnetAddress);

                                            vertx.eventBus().send(AlertService.ADDRESS_ALERT_PUBLISH, alertMsg);

                                        }

                                    }

                                    if (vertx != null && vertx.eventBus() != null) {

                                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                                .put("eventType", "SCAN_COMPLETED")
                                                .put("eventContext", "Completed ICMP scan for Subnet '" + subnetName + "' (" + allUpIps.size() + " UP, " + allDownIps.size() + " DOWN)")
                                                .put("severity", 1));

                                    } else {

                                        mysqlPool.preparedQuery(DbQueries.INSERT_EVENT)
                                                .execute(Tuple.of("SCAN_COMPLETED", "Completed ICMP scan for Subnet '" + subnetName + "' (" + allUpIps.size() + " UP, " + allDownIps.size() + " DOWN)", 1, null))
                                                .onFailure(err -> logger.debug("Could not log scan event: {}", err.getMessage()));

                                    }

                                    return summary;

                                });

                    }

                    List<String> chunkIps = new ArrayList<>(DEFAULT_CHUNK_SIZE);

                    long maxId = lastSeenId;

                    for (Row row : rows) {

                        Long id = DbUtil.getLong(row, "id");

                        if (id != null && id > maxId) {

                            maxId = id;

                        }

                        String ip = DbUtil.getString(row, "ip_address");

                        if (!ip.isEmpty()) {

                            chunkIps.add(ip);

                        }

                    }

                    final long nextLastId = maxId;

                    String commaSeparatedIps = String.join(",", chunkIps);

                    JsonObject pingPayload = new JsonObject()
                            .put("ip-addresses", commaSeparatedIps)
                            .put("max-ping-check-retry-count", 1)
                            .put("max-ping-check-timeout", 400)
                            .put("max-concurrent-ping", 256);

                    return goPluginBridge.execute("ping", pingPayload.encode())
                            .compose(pingResult -> {

                                JsonArray up = pingResult.getJsonArray("up", new JsonArray());

                                JsonArray down = pingResult.getJsonArray("down", new JsonArray());

                                for (int i = 0; i < up.size(); i++) {

                                    allUpIps.add(up.getString(i));

                                }

                                for (int i = 0; i < down.size(); i++) {

                                    allDownIps.add(down.getString(i));

                                }

                                // Update in-memory live progress for UI polling
                                JsonObject currentProgress = activeScans.get(subnetId);

                                if (currentProgress != null) {

                                    currentProgress.put("processedIps", allUpIps.size() + allDownIps.size());

                                    currentProgress.put("chunksProcessed", chunksProcessed + 1);

                                }

                                return updateIpStatusBatch(subnetId, up, down)
                                        .compose(v -> streamAndScanChunks(
                                                subnetId,
                                                subnetName,
                                                subnetAddress,
                                                subnetCidr,
                                                nextLastId,
                                                totalIps,
                                                allUpIps,
                                                allDownIps,
                                                startTime,
                                                chunksProcessed + 1
                                        ));

                            });

                });

    }

    /**
     * Returns the current scan status for a specific subnet.
     */
    public JsonObject getScanStatus(Long subnetId) {

        if (subnetId == null) {

            return new JsonObject().put("status", "IDLE");

        }

        JsonObject status = activeScans.get(subnetId);

        if (status == null) {

            return new JsonObject()
                    .put("subnetId", subnetId)
                    .put("status", "IDLE")
                    .put("message", "No recent scan recorded");

        }

        return status;

    }

    /**
     * Returns the first active scan in progress across all subnets, if any.
     */
    public JsonObject getAnyActiveScan() {

        for (Map.Entry<Long, JsonObject> entry : activeScans.entrySet()) {

            JsonObject st = entry.getValue();

            if (st != null && "IN_PROGRESS".equalsIgnoreCase(st.getString("status"))) {

                return st;

            }

        }

        return null;

    }

    /**
     * Batch updates responding IPs to USED status, and offline previously-USED IPs to AVAILABLE status in MariaDB.
     */
    private Future<Void> updateIpStatusBatch(Long subnetId, JsonArray upIps, JsonArray downIps) {

        List<Future> updateFutures = new ArrayList<>();

        if (upIps != null && !upIps.isEmpty()) {

            String updateUsedSql = DbQueries.SCANNER_UPDATE_IP_STATUS_USED;

            List<Tuple> usedBatch = new ArrayList<>(upIps.size());

            for (int i = 0; i < upIps.size(); i++) {

                String ip = upIps.getString(i);

                if (ip != null && !ip.isBlank()) {

                    usedBatch.add(Tuple.of(subnetId, ip.trim()));

                }

            }

            updateFutures.add(mysqlPool.preparedQuery(updateUsedSql).executeBatch(usedBatch).mapEmpty());

        }

        if (downIps != null && !downIps.isEmpty()) {

            String updateAvailSql = DbQueries.SCANNER_UPDATE_IP_STATUS_AVAILABLE;

            List<Tuple> availBatch = new ArrayList<>(downIps.size());

            for (int i = 0; i < downIps.size(); i++) {

                String ip = downIps.getString(i);

                if (ip != null && !ip.isBlank()) {

                    availBatch.add(Tuple.of(subnetId, ip.trim()));

                }

            }

            updateFutures.add(mysqlPool.preparedQuery(updateAvailSql).executeBatch(availBatch).mapEmpty());

        }

        if (updateFutures.isEmpty()) {

            return Future.succeededFuture();

        }

        return io.vertx.core.CompositeFuture.all(updateFutures).mapEmpty();

    }

    /**
     * Atomic SQL aggregation query to refresh subnet utilization counters in subnet_details.
     */
    private Future<Void> recalculateSubnetCounters(Long subnetId) {

        String updateCountersSql = DbQueries.SCANNER_RECALCULATE_COUNTERS;

        return mysqlPool.preparedQuery(updateCountersSql).execute(Tuple.of(subnetId, subnetId)).mapEmpty();

    }

}
