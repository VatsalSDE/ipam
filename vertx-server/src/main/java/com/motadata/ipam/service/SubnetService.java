package com.motadata.ipam.service;

import com.motadata.ipam.database.DbQueries;

import com.motadata.ipam.util.IPv4Util;

import com.motadata.ipam.verticle.SubnetWorkerVerticle;

import io.vertx.core.Future;

import io.vertx.core.Promise;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonArray;

import io.vertx.core.json.JsonObject;

import io.vertx.mysqlclient.MySQLPool;

import io.vertx.sqlclient.Row;

import io.vertx.sqlclient.RowSet;

import io.vertx.sqlclient.Tuple;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import java.util.List;

/**
 * Enterprise Subnet Management Service.
 * Implements O(1) bitwise CIDR validation, overlap detection, memory-safe
 * batch IP generation, real-time utilization metrics, and cascading deletions.
 */
public class SubnetService {

    private static final Logger logger = LoggerFactory.getLogger(SubnetService.class);

    private static final int DEFAULT_CHUNK_SIZE = 512;

    private final MySQLPool mysqlPool;

    private final Vertx vertx;

    public SubnetService(MySQLPool mysqlPool) {

        this(mysqlPool, null);

    }

    public SubnetService(MySQLPool mysqlPool, Vertx vertx) {

        this.mysqlPool = mysqlPool;

        this.vertx = vertx;

    }

    /**
     * Lists subnets with pagination, optional search, and calculated utilization metrics.
     */
    public Future<JsonObject> listSubnets(int limit, int offset, String search) {

        if (limit <= 0) {

            limit = 50;

        }

        if (offset < 0) {

            offset = 0;

        }

        boolean hasSearch = search != null && !search.isBlank();

        String countSql = hasSearch ? DbQueries.COUNT_SUBNETS_SEARCH : DbQueries.COUNT_SUBNETS_ALL;

        String selectSql = hasSearch ? DbQueries.LIST_SUBNETS_SEARCH : DbQueries.LIST_SUBNETS_ALL;

        final int finalLimit = limit;

        final int finalOffset = offset;

        Promise<JsonObject> promise = Promise.promise();

        Tuple countTuple = hasSearch ? Tuple.of("%" + search.trim() + "%", "%" + search.trim() + "%") : Tuple.tuple();

        mysqlPool.preparedQuery(countSql).execute(countTuple)
                .compose(countRows -> {

                    long totalCount = 0;

                    if (countRows.iterator().hasNext()) {

                        totalCount = countRows.iterator().next().getLong("total");

                    }

                    final long total = totalCount;

                    Tuple selectTuple = hasSearch
                            ? Tuple.of("%" + search.trim() + "%", "%" + search.trim() + "%", finalLimit, finalOffset)
                            : Tuple.of(finalLimit, finalOffset);

                    return mysqlPool.preparedQuery(selectSql).execute(selectTuple)
                            .map(rows -> {

                                JsonArray subnets = new JsonArray();

                                for (Row row : rows) {

                                    JsonObject subnet = mapSubnetRow(row);

                                    subnets.add(subnet);

                                }

                                JsonObject result = new JsonObject();

                                result.put("total", total);

                                result.put("limit", finalLimit);

                                result.put("offset", finalOffset);

                                result.put("subnets", subnets);

                                return result;

                            });

                })
                .onSuccess(promise::complete)
                .onFailure(promise::fail);

        return promise.future();

    }

    /**
     * Fetches a single subnet's details along with its real-time IP status breakdown.
     */
    public Future<JsonObject> getSubnetById(Long subnetId) {

        if (subnetId == null || subnetId <= 0) {

            return Future.failedFuture("Invalid subnet ID");

        }

        String subnetSql = DbQueries.GET_SUBNET_BY_ID;

        String statusBreakdownSql = DbQueries.GET_SUBNET_STATUS_BREAKDOWN;

        return mysqlPool.preparedQuery(subnetSql).execute(Tuple.of(subnetId))
                .compose(rows -> {

                    if (!rows.iterator().hasNext()) {

                        return Future.failedFuture("Subnet with ID " + subnetId + " not found");

                    }

                    JsonObject subnet = mapSubnetRow(rows.iterator().next());

                    return mysqlPool.preparedQuery(statusBreakdownSql).execute(Tuple.of(subnetId))
                            .map(breakdownRows -> {

                                JsonObject statusSummary = new JsonObject();

                                statusSummary.put("available", 0L);

                                statusSummary.put("used", 0L);

                                statusSummary.put("reserved", 0L);

                                statusSummary.put("transient", 0L);

                                for (Row bRow : breakdownRows) {

                                    String status = bRow.getString("status");

                                    Long count = bRow.getLong("count");

                                    if (status != null) {

                                        statusSummary.put(status.toLowerCase(), count);

                                    }

                                }

                                subnet.put("statusSummary", statusSummary);

                                return subnet;

                            });

                });

    }

    /**
     * Creates a new Subnet, validates CIDR, detects overlaps, inserts record,
     * and batch-inserts IP records in memory-safe chunks.
     */
    public Future<JsonObject> createSubnet(JsonObject payload) {

        if (payload == null) {

            return Future.failedFuture("Request payload is required");

        }

        String rawAddress = payload.getString("subnetAddress");

        String subnetName = payload.getString("subnetName");

        Integer cidr = payload.getInteger("subnetCidr");

        // Handle legacy UI format: "maskInfo": "255.255.255.0/24"
        String maskInfo = payload.getString("maskInfo");

        if (maskInfo != null && maskInfo.contains("/")) {

            String[] parts = maskInfo.split("/");

            if (parts.length > 1) {

                try {

                    cidr = Integer.parseInt(parts[1].trim());

                } catch (NumberFormatException ignored) {}

            }

        }

        if (rawAddress == null || rawAddress.isBlank()) {

            return Future.failedFuture("Subnet address is required");

        }

        if (subnetName == null || subnetName.isBlank()) {

            return Future.failedFuture("Subnet name is required");

        }

        if (cidr == null || cidr < 8 || cidr > 30) {

            return Future.failedFuture("Subnet CIDR prefix must be between 8 and 30");

        }

        if (!IPv4Util.isValidIpv4(rawAddress)) {

            return Future.failedFuture("Invalid IPv4 address: " + rawAddress);

        }

        long inputIpLong = IPv4Util.ipToLong(rawAddress);

        long networkLong = IPv4Util.getNetworkAddress(inputIpLong, cidr);

        long broadcastLong = IPv4Util.getBroadcastAddress(networkLong, cidr);

        String normalizedAddress = IPv4Util.longToIp(networkLong);

        String subnetMask = IPv4Util.prefixToMaskString(cidr);

        long totalIp = IPv4Util.getUsableHostCount(cidr);

        long availableIp = totalIp;

        String description = payload.getString("description", "");

        String location = payload.getString("location", "");

        boolean scheduleStatus = payload.getBoolean("scheduleStatus", false);

        int scheduleHour = payload.getInteger("scheduleHour", 0);

        final int finalCidr = cidr;

        return checkDuplicateAndOverlap(subnetName, normalizedAddress, networkLong, broadcastLong)
                .compose(v -> {

                    String insertSubnetSql = DbQueries.INSERT_SUBNET;

                    Tuple tuple = Tuple.of(
                            subnetName.trim(),
                            normalizedAddress,
                            finalCidr,
                            subnetMask,
                            description,
                            location,
                            totalIp,
                            availableIp,
                            scheduleStatus ? 1 : 0,
                            scheduleHour
                    );

                    return mysqlPool.preparedQuery(insertSubnetSql).execute(tuple);

                })
                .compose(insertResult -> {

                    Long newSubnetId = insertResult.property(io.vertx.mysqlclient.MySQLClient.LAST_INSERTED_ID);

                    if (newSubnetId == null || newSubnetId <= 0) {

                        return fetchSubnetIdByName(subnetName);

                    }

                    return Future.succeededFuture(newSubnetId);

                })
                .compose(createdId -> {

                    return batchInsertIpRecords(createdId, networkLong, broadcastLong)
                            .map(v -> {

                                JsonObject created = new JsonObject();

                                created.put("id", createdId);

                                created.put("subnetName", subnetName);

                                created.put("subnetAddress", normalizedAddress);

                                created.put("subnetCidr", finalCidr);

                                created.put("subnetMask", subnetMask);

                                created.put("totalIp", totalIp);

                                created.put("availableIp", availableIp);

                                created.put("usedIp", 0L);

                                created.put("usedIpPercentage", 0.0);

                                created.put("description", description);

                                created.put("location", location);

                                return created;

                            });

                });

    }

    /**
     * Fetches paginated IP address rows for a specific subnet.
     */
    public Future<JsonObject> getSubnetIps(Long subnetId, int limit, int offset, String statusFilter, String search) {

        if (subnetId == null || subnetId <= 0) {

            return Future.failedFuture("Invalid subnet ID");

        }

        if (limit <= 0) {

            limit = 50;

        }

        if (offset < 0) {

            offset = 0;

        }

        StringBuilder countSql = new StringBuilder(DbQueries.COUNT_SUBNET_IPS_BASE);

        StringBuilder selectSql = new StringBuilder(DbQueries.SELECT_SUBNET_IPS_BASE);

        List<Object> params = new ArrayList<>();

        params.add(subnetId);

        if (statusFilter != null && !statusFilter.isBlank()) {

            countSql.append(" AND status = ?");

            selectSql.append(" AND status = ?");

            params.add(statusFilter.trim().toUpperCase());

        }

        if (search != null && !search.isBlank()) {

            countSql.append(" AND (ip_address LIKE ? OR host_name LIKE ? OR mac_address LIKE ?)");

            selectSql.append(" AND (ip_address LIKE ? OR host_name LIKE ? OR mac_address LIKE ?)");

            String wildcard = "%" + search.trim() + "%";

            params.add(wildcard);

            params.add(wildcard);

            params.add(wildcard);

        }

        selectSql.append(" ORDER BY id ASC LIMIT ? OFFSET ?");

        List<Object> selectParams = new ArrayList<>(params);

        selectParams.add(limit);

        selectParams.add(offset);

        final int finalLimit = limit;

        final int finalOffset = offset;

        return mysqlPool.preparedQuery(countSql.toString()).execute(Tuple.from(params))
                .compose(countRows -> {

                    long total = 0;

                    if (countRows.iterator().hasNext()) {

                        total = countRows.iterator().next().getLong("total");

                    }

                    final long totalRecords = total;

                    return mysqlPool.preparedQuery(selectSql.toString()).execute(Tuple.from(selectParams))
                            .map(rows -> {

                                JsonArray ips = new JsonArray();

                                for (Row row : rows) {

                                    JsonObject ipObj = new JsonObject();

                                    ipObj.put("id", row.getLong("id"));

                                    ipObj.put("ipAddress", row.getString("ip_address"));

                                    ipObj.put("macAddress", row.getString("mac_address"));

                                    ipObj.put("status", row.getString("status"));

                                    ipObj.put("deviceType", row.getString("device_type"));

                                    ipObj.put("hostName", row.getString("host_name"));

                                    ipObj.put("authenticity", row.getString("authenticity"));

                                    ips.add(ipObj);

                                }

                                JsonObject result = new JsonObject();

                                result.put("total", totalRecords);

                                result.put("limit", finalLimit);

                                result.put("offset", finalOffset);

                                result.put("ips", ips);

                                return result;

                            });

                });

    }

    /**
     * Cascades deletion of a subnet and all its associated IP address records.
     */
    public Future<JsonObject> deleteSubnet(Long subnetId) {

        if (subnetId == null || subnetId <= 0) {

            return Future.failedFuture("Invalid subnet ID");

        }

        String deleteIpsSql = DbQueries.DELETE_SUBNET_IPS;

        String deleteSubnetSql = DbQueries.DELETE_SUBNET_BY_ID;

        return mysqlPool.preparedQuery(deleteIpsSql).execute(Tuple.of(subnetId))
                .compose(ipResult -> mysqlPool.preparedQuery(deleteSubnetSql).execute(Tuple.of(subnetId)))
                .map(subnetResult -> {

                    JsonObject result = new JsonObject();

                    result.put("deleted", true);

                    result.put("subnetId", subnetId);

                    return result;

                });

    }

    /**
     * Validates name/address duplicates and CIDR overlaps against all existing subnets.
     */
    private Future<Void> checkDuplicateAndOverlap(String name, String normalizedAddress, long newStart, long newEnd) {

        String query = DbQueries.GET_ALL_SUBNETS_FOR_OVERLAP;

        return mysqlPool.preparedQuery(query).execute()
                .compose(rows -> {

                    for (Row row : rows) {

                        String existingName = row.getString("subnetName");

                        String existingAddress = row.getString("subnetAddress");

                        Integer existingCidr = row.getInteger("subnetCidr");

                        if (name.equalsIgnoreCase(existingName)) {

                            return Future.failedFuture("Subnet name '" + name + "' already exists");

                        }

                        if (normalizedAddress.equalsIgnoreCase(existingAddress)) {

                            return Future.failedFuture("Subnet address '" + normalizedAddress + "' already exists");

                        }

                        if (existingAddress != null && existingCidr != null && IPv4Util.isValidIpv4(existingAddress)) {

                            long existingStart = IPv4Util.getNetworkAddress(IPv4Util.ipToLong(existingAddress), existingCidr);

                            long existingEnd = IPv4Util.getBroadcastAddress(existingStart, existingCidr);

                            if (IPv4Util.isOverlapping(newStart, newEnd, existingStart, existingEnd)) {

                                return Future.failedFuture("Subnet range overlaps with existing subnet '" + existingName +
                                        "' (" + existingAddress + "/" + existingCidr + ")");

                            }

                        }

                    }

                    return Future.succeededFuture();

                });

    }

    /**
     * Populates subnet IP records using true streaming recursion or EventBus dispatch to SubnetWorkerVerticle.
     * Ensures constant O(1) heap memory footprint (< 50 KB) without pre-allocating IP string lists.
     */
    private Future<Void> batchInsertIpRecords(Long subnetId, long networkLong, long broadcastLong) {

        long firstUsable = networkLong + 1;

        long lastUsable = broadcastLong - 1;

        if (firstUsable > lastUsable) {

            return Future.succeededFuture();

        }

        if (vertx != null && vertx.eventBus() != null) {

            JsonObject msg = new JsonObject()
                    .put("subnetId", subnetId)
                    .put("networkLong", networkLong)
                    .put("broadcastLong", broadcastLong)
                    .put("chunkSize", DEFAULT_CHUNK_SIZE);

            return vertx.eventBus().<JsonObject>request(SubnetWorkerVerticle.ADDRESS_POPULATE_IPS, msg)
                    .mapEmpty();

        }

        return streamInsertIpChunks(subnetId, firstUsable, lastUsable, DEFAULT_CHUNK_SIZE);

    }

    /**
     * Recursively streams IP batches of 512 to MariaDB with strictly constant O(1) JVM heap memory.
     */
    private Future<Void> streamInsertIpChunks(Long subnetId, long currentIp, long lastUsableIp, int chunkSize) {

        if (currentIp > lastUsableIp) {

            return Future.succeededFuture();

        }

        long chunkEndIp = Math.min(currentIp + chunkSize - 1, lastUsableIp);

        int batchSize = (int) (chunkEndIp - currentIp + 1);

        List<Tuple> batch = new ArrayList<>(batchSize);

        for (long ip = currentIp; ip <= chunkEndIp; ip++) {

            batch.add(Tuple.of(subnetId, IPv4Util.longToIp(ip)));

        }

        return mysqlPool.preparedQuery(DbQueries.INSERT_SUBNET_IPS_BATCH)
                .executeBatch(batch)
                .compose(res -> streamInsertIpChunks(subnetId, chunkEndIp + 1, lastUsableIp, chunkSize));

    }

    private Future<Long> fetchSubnetIdByName(String subnetName) {

        return mysqlPool.preparedQuery(DbQueries.FIND_SUBNET_ID_BY_NAME)
                .execute(Tuple.of(subnetName))
                .map(rows -> {

                    if (rows.iterator().hasNext()) {

                        return rows.iterator().next().getLong("id");

                    }

                    return null;

                });

    }

    private JsonObject mapSubnetRow(Row row) {

        JsonObject obj = new JsonObject();

        Long id = row.getLong("id");

        Long totalIp = row.getLong("totalIp");

        Long usedIp = row.getLong("usedIp");

        Long availableIp = row.getLong("availableIp");

        if (totalIp == null) totalIp = 0L;

        if (usedIp == null) usedIp = 0L;

        if (availableIp == null) availableIp = 0L;

        double usedPercent = totalIp > 0 ? ((double) usedIp / totalIp) * 100.0 : 0.0;

        obj.put("id", id);

        obj.put("subnetName", row.getString("subnetName"));

        obj.put("subnetAddress", row.getString("subnetAddress"));

        obj.put("subnetCidr", row.getInteger("subnetCidr"));

        obj.put("subnetMask", row.getString("subnetMask"));

        obj.put("description", row.getString("description"));

        obj.put("location", row.getString("location"));

        obj.put("totalIp", totalIp);

        obj.put("usedIp", usedIp);

        obj.put("availableIp", availableIp);

        obj.put("usedIpPercentage", Math.round(usedPercent * 100.0) / 100.0);

        obj.put("scheduleStatus", row.getBoolean("scheduleStatus"));

        obj.put("scheduleHour", row.getInteger("scheduleHour"));

        return obj;

    }

}
