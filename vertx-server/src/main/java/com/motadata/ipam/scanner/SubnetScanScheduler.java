package com.motadata.ipam.scanner;


import com.motadata.ipam.core.database.DbQueries;
import com.motadata.ipam.core.database.DbUtil;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import io.vertx.sqlclient.Pool;

import io.vertx.sqlclient.Row;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.time.Instant;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-blocking Periodic Subnet Scan Scheduler.
 * Replaces legacy heavy Quartz scheduler with native Vert.x periodic timers.
 * Inspects subnets configured with scheduleStatus = 1 and triggers automated sweeps.
 */
public class SubnetScanScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SubnetScanScheduler.class);

    private final Vertx vertx;

    private final Pool mysqlPool;

    private final ScannerService scannerService;

    // Subnet ID -> Last scanned timestamp in milliseconds
    private final Map<Long, Long> lastScannedMap = new ConcurrentHashMap<>();

    private Long periodicTimerId;

    public SubnetScanScheduler(Vertx vertx, Pool mysqlPool, ScannerService scannerService) {

        this.vertx = vertx;

        this.mysqlPool = mysqlPool;

        this.scannerService = scannerService;

    }

    /**
     * Starts the periodic scheduler. Checks scheduled subnets every pollIntervalMs.
     */
    public void start(long pollIntervalMs) {

        if (periodicTimerId != null) {

            return;

        }

        logger.info("Starting SubnetScanScheduler with polling interval: {} ms", pollIntervalMs);

        // 1. Listen for ANY completed scan (Manual UI click OR Scheduled) to update the in-memory map
        if (vertx != null && vertx.eventBus() != null) {

            vertx.eventBus().<JsonObject>consumer("ipam.subnet.scan.completed", message -> {

                JsonObject body = message.body();

                if (body != null && body.containsKey("subnetId")) {

                    Long subnetId = body.getLong("subnetId");

                    Long completedAt = body.getLong("completedAt", Instant.now().toEpochMilli());

                    lastScannedMap.put(subnetId, completedAt);

                    logger.debug("SubnetScanScheduler updated timestamp for Subnet ID {} to {}", subnetId, completedAt);

                }

            });

        }

        // 2. Start the periodic checker
        periodicTimerId = vertx.setPeriodic(pollIntervalMs, id -> checkAndTriggerScheduledScans());

    }

    /**
     * Public method to manually record a scan timestamp for a subnet if called directly.
     */
    public void recordScan(Long subnetId) {

        if (subnetId != null) {

            this.lastScannedMap.put(subnetId, Instant.now().toEpochMilli());

        }

    }

    /**
     * Stops the periodic scheduler.
     */
    public void stop() {

        if (periodicTimerId != null) {

            vertx.cancelTimer(periodicTimerId);

            periodicTimerId = null;

            logger.info("SubnetScanScheduler stopped");

        }

    }

    /**
     * Queries database for active schedules and triggers scans if due.
     */
    public void checkAndTriggerScheduledScans() {

        String sql = DbQueries.SCHEDULER_FIND_DUE_SUBNETS;

        mysqlPool.preparedQuery(sql).execute()
                .onSuccess(rows -> {

                    long now = Instant.now().toEpochMilli();

                    for (Row row : rows) {

                        Long subnetId = DbUtil.getLong(row, "id");

                        String subnetName = DbUtil.getString(row, "subnetName");

                        Integer scheduleHour = DbUtil.getIntOrDefault(row, "scheduleHour", 24);

                        if (scheduleHour <= 0) {

                            scheduleHour = 24; // Default to daily if not set

                        }

                        long intervalMs = (long) scheduleHour * 3600 * 1000;

                        Long lastScanned = lastScannedMap.get(subnetId);

                        if (lastScanned == null || (now - lastScanned) >= intervalMs) {

                            logger.info("Subnet '{}' (ID: {}) is due for scheduled scan (every {} hours)", subnetName, subnetId, scheduleHour);

                            lastScannedMap.put(subnetId, now);

                            scannerService.triggerScan(subnetId)
                                    .onSuccess(res -> logger.info("Scheduled scan completed for subnet '{}': {} UP", subnetName, res.getInteger("upCount")))
                                    .onFailure(err -> logger.error("Scheduled scan failed for subnet '{}': {}", subnetName, err.getMessage()));

                        }

                    }

                })
                .onFailure(err -> logger.warn("Failed to check scheduled subnets: {}", err.getMessage()));

    }

}
