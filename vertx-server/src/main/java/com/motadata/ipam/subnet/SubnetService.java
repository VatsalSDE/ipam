package com.motadata.ipam.subnet;

import com.motadata.ipam.core.database.DbQueries;

import com.motadata.ipam.core.database.DbUtil;

import com.motadata.ipam.event.EventService;

import io.vertx.core.Future;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonArray;

import io.vertx.core.json.JsonObject;

import io.vertx.mysqlclient.MySQLClient;

import io.vertx.sqlclient.Pool;

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

    private final Pool mysqlPool;

    private final Vertx vertx;

    private final SubnetIpService subnetIpService;

    public SubnetService(Pool mysqlPool) {

        this(mysqlPool, null);

    }

    public SubnetService(Pool mysqlPool, Vertx vertx) {

        this.mysqlPool = mysqlPool;

        this.vertx = vertx;

        this.subnetIpService = new SubnetIpService(mysqlPool);

    }

    /**
     * Lists subnets with pagination, optional search, and calculated utilization metrics.
     */
    public Future<JsonObject> listSubnets(int limit, int offset, String search) {

        if (limit <= 0) {

            limit = 50;

        } else if (limit > 500) {

            limit = 500;

        }

        if (offset < 0) {

            offset = 0;

        }

        boolean hasSearch = search != null && !search.isBlank();

        String countSql = hasSearch ? DbQueries.COUNT_SUBNETS_SEARCH : DbQueries.COUNT_SUBNETS_ALL;

        String selectSql = hasSearch ? DbQueries.LIST_SUBNETS_SEARCH : DbQueries.LIST_SUBNETS_ALL;

        final int finalLimit = limit;

        final int finalOffset = offset;

        Tuple countTuple = hasSearch ? Tuple.of("%" + search.trim() + "%", "%" + search.trim() + "%") : Tuple.tuple();

        return mysqlPool.preparedQuery(countSql).execute(countTuple)
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

                });

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

                                    String status = DbUtil.getString(bRow, "status");

                                    Long count = DbUtil.getLongOrDefault(bRow, "count", 0L);

                                    if (!status.isEmpty()) {

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
                .compose((RowSet<Row> insertResult) -> {

                    Long newSubnetId = insertResult.property(MySQLClient.LAST_INSERTED_ID);

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

                                if (vertx != null && vertx.eventBus() != null) {

                                    vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                            .put("eventType", "SUBNET_CREATED")
                                            .put("eventContext", "Created Subnet '" + subnetName + "' (" + normalizedAddress + "/" + finalCidr + ") with " + totalIp + " IPs")
                                            .put("severity", 1));

                                }

                                return created;

                            });

                });

    }

    /**
     * Fetches paginated IP address rows for a specific subnet by delegating to SubnetIpService.
     */
    public Future<JsonObject> getSubnetIps(Long subnetId, int limit, int offset, String statusFilter, String search) {

        return subnetIpService.getSubnetIps(subnetId, limit, offset, statusFilter, search);

    }

    /**
     * Cascades deletion of a subnet and all its associated IP addresses, alerts, supernet links, and requests.
     */
    public Future<JsonObject> deleteSubnet(Long subnetId) {

        if (subnetId == null || subnetId <= 0) {

            return Future.failedFuture("Invalid subnet ID");

        }

        String deleteIpsSql = DbQueries.DELETE_SUBNET_IPS;

        String deleteAlertsSql = DbQueries.CLEAR_ALERTS_BY_SUBNET;

        String deleteSubnetSql = DbQueries.DELETE_SUBNET_BY_ID;

        return mysqlPool.preparedQuery(deleteIpsSql).execute(Tuple.of(subnetId))
                .compose(ipResult -> mysqlPool.preparedQuery(deleteAlertsSql).execute(Tuple.of(subnetId)))
                .compose(alertResult -> mysqlPool.preparedQuery("DELETE FROM supernet_details WHERE subnet_id = ?").execute(Tuple.of(String.valueOf(subnetId))))
                .compose(supernetResult -> mysqlPool.preparedQuery("DELETE FROM ip_requests WHERE subnet_id = ?").execute(Tuple.of(String.valueOf(subnetId))))
                .compose(reqResult -> mysqlPool.preparedQuery(deleteSubnetSql).execute(Tuple.of(subnetId)))
                .map(subnetResult -> {

                    JsonObject result = new JsonObject();

                    result.put("deleted", true);

                    result.put("subnetId", subnetId);

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "SUBNET_DELETED")
                                .put("eventContext", "Deleted Subnet ID " + subnetId + " and cascaded cleanup of all IPs, alerts, and requests")
                                .put("severity", 2));

                    }

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

        Long id = DbUtil.getLong(row, "id");

        Long totalIp = DbUtil.getLongOrDefault(row, "totalIp", 0L);

        Long usedIp = DbUtil.getLongOrDefault(row, "usedIp", 0L);

        Long availableIp = DbUtil.getLongOrDefault(row, "availableIp", 0L);

        Long transientIp = DbUtil.getLongOrDefault(row, "transientIp", 0L);

        double usedPercent = totalIp > 0 ? ((double) usedIp / totalIp) * 100.0 : 0.0;

        obj.put("id", id);

        obj.put("subnetName", DbUtil.getString(row, "subnetName"));

        obj.put("subnetAddress", DbUtil.getString(row, "subnetAddress"));

        obj.put("subnetCidr", DbUtil.getIntOrDefault(row, "subnetCidr", 24));

        obj.put("subnetMask", DbUtil.getString(row, "subnetMask"));

        obj.put("description", DbUtil.getString(row, "description"));

        obj.put("location", DbUtil.getString(row, "location"));

        obj.put("totalIp", totalIp);

        obj.put("usedIp", usedIp);

        obj.put("availableIp", availableIp);

        obj.put("transientIp", transientIp);

        obj.put("usedIpPercentage", Math.round(usedPercent * 100.0) / 100.0);

        obj.put("scheduleStatus", DbUtil.getBoolean(row, "scheduleStatus"));

        obj.put("status", DbUtil.getString(row, "status"));

        String lastScan = DbUtil.getString(row, "lastScanTime");

        obj.put("lastScanTime", lastScan != null && !lastScan.isBlank() ? lastScan : "Never");

        return obj;

    }

    /**
     * Retrieves specific IP details by IP record ID for modal/summary inspection.
     */
    public Future<JsonObject> getIpDetails(Long ipId) {

        if (ipId == null || ipId <= 0) {

            return Future.failedFuture("Invalid IP ID");

        }

        return mysqlPool.preparedQuery(DbQueries.SELECT_SUBNET_IP_BY_ID)
                .execute(Tuple.of(ipId))
                .map(rows -> {

                    if (!rows.iterator().hasNext()) {

                        return null;

                    }

                    Row row = rows.iterator().next();

                    JsonObject obj = new JsonObject();

                    obj.put("id", DbUtil.getLong(row, "id"));

                    obj.put("ipAddress", DbUtil.getString(row, "ip_address"));

                    String mac = DbUtil.getString(row, "macAddress");

                    obj.put("macAddress", mac.isBlank() ? "N/A" : mac);

                    String st = DbUtil.getString(row, "status");

                    obj.put("status", st.isBlank() ? "AVAILABLE" : st);

                    String dt = DbUtil.getString(row, "deviceType");

                    obj.put("deviceType", dt.isBlank() ? "N/A" : dt);

                    String hn = DbUtil.getString(row, "hostName");

                    obj.put("hostName", hn.isBlank() ? "N/A" : hn);

                    String auth = DbUtil.getString(row, "authenticity");

                    obj.put("authenticity", auth.isBlank() ? "N/A" : auth);

                    String fwd = DbUtil.getString(row, "ipToDns");

                    obj.put("ipToDns", fwd.isBlank() ? "N/A" : fwd);

                    String rev = DbUtil.getString(row, "dnsToIp");

                    obj.put("dnsToIp", rev.isBlank() ? "N/A" : rev);

                    String alive = DbUtil.getString(row, "lastAliveTime");

                    obj.put("lastAliveTime", alive.isBlank() ? "N/A" : alive);

                    return obj;

                });

    }

    /**
     * Retrieves audit changelog history specifically for an individual IP record ID.
     */
    public Future<JsonArray> getIpChangeLogs(Long ipId, int limit) {

        if (ipId == null || ipId <= 0) {

            return Future.failedFuture("Invalid IP ID");

        }

        int queryLimit = (limit > 0 && limit <= 100) ? limit : 50;

        return mysqlPool.preparedQuery(DbQueries.SELECT_IP_CHANGE_LOG_BY_IP_ID)
                .execute(Tuple.of(ipId, queryLimit))
                .map(rows -> {

                    JsonArray list = new JsonArray();

                    for (Row row : rows) {

                        JsonObject obj = new JsonObject();

                        obj.put("id", DbUtil.getLong(row, "id"));

                        obj.put("ipAddressId", DbUtil.getLong(row, "ipAddressId"));

                        obj.put("subnetId", DbUtil.getLong(row, "subnetId"));

                        obj.put("timestamp", DbUtil.getString(row, "timestamp"));

                        obj.put("user", DbUtil.getString(row, "user"));

                        obj.put("ip", DbUtil.getString(row, "ip"));

                        obj.put("changelog", DbUtil.getString(row, "changelog"));

                        list.add(obj);

                    }

                    return list;

                });

    }

    /**
     * Updates the status of an IP range within a subnet (Available, Used, Transient, Reserved)

     * and records audit changelog entries in MariaDB.
     */
    public Future<JsonObject> updateIpRangeStatus(Long subnetId, String startIp, String endIp, String status, String username) {

        if (subnetId == null || subnetId <= 0) {

            return Future.failedFuture("Invalid Subnet ID");

        }

        if (startIp == null || startIp.isBlank() || endIp == null || endIp.isBlank() || status == null || status.isBlank()) {

            return Future.failedFuture("Subnet ID, start IP, end IP, and status are required");

        }

        String sIp = startIp.trim();

        String eIp = endIp.trim();

        if (!IPv4Util.isValidIpv4(sIp)) {

            return Future.failedFuture("Invalid start IP address format: " + sIp);

        }

        if (!IPv4Util.isValidIpv4(eIp)) {

            return Future.failedFuture("Invalid end IP address format: " + eIp);

        }

        long sLong = IPv4Util.ipToLong(sIp);

        long eLong = IPv4Util.ipToLong(eIp);

        if (sLong > eLong) {

            return Future.failedFuture("Start IP (" + sIp + ") must be less than or equal to End IP (" + eIp + ")");

        }

        String normStatus = status.trim().toUpperCase();

        String user = (username != null && !username.isBlank()) ? username : "System";

        String logMsg = "Status updated to " + normStatus + " by " + user;

        return mysqlPool.preparedQuery(DbQueries.GET_SUBNET_BY_ID)
                .execute(Tuple.of(subnetId))
                .compose(subRows -> {

                    if (!subRows.iterator().hasNext()) {

                        return Future.failedFuture("Subnet not found with ID: " + subnetId);

                    }

                    Row subRow = subRows.iterator().next();

                    String subAddr = DbUtil.getString(subRow, "subnetAddress");

                    int cidr = DbUtil.getIntOrDefault(subRow, "subnetCidr", 24);

                    long netLong = IPv4Util.getNetworkAddress(IPv4Util.ipToLong(subAddr), cidr);

                    long bcastLong = IPv4Util.getBroadcastAddress(netLong, cidr);

                    long firstUsable = netLong + 1;

                    long lastUsable = bcastLong - 1;

                    if (sLong < firstUsable || eLong > lastUsable) {

                        return Future.failedFuture("IP range (" + sIp + " - " + eIp + ") is outside the valid host range (" +
                                IPv4Util.longToIp(firstUsable) + " - " + IPv4Util.longToIp(lastUsable) + ") for Subnet " + subAddr + "/" + cidr);

                    }

                    return mysqlPool.preparedQuery(DbQueries.UPDATE_SUBNET_IP_RANGE_STATUS)
                            .execute(Tuple.of(normStatus, subnetId, sIp, eIp))
                            .compose(updateResult -> {

                                int affected = updateResult.rowCount();

                                if (affected == 0) {

                                    return Future.failedFuture("No matching IP addresses found in the specified range (" + sIp + " - " + eIp + ") for Subnet " + subAddr);

                                }

                                return mysqlPool.preparedQuery(DbQueries.INSERT_IP_RANGE_CHANGE_LOG)
                                        .execute(Tuple.of(user, logMsg, subnetId, sIp, eIp))
                                        .compose(r -> mysqlPool.preparedQuery(DbQueries.SYNC_SUBNET_IP_COUNTS_BY_ID)
                                                .execute(Tuple.of(subnetId, subnetId)))
                                        .map(r -> {

                                            if (vertx != null && vertx.eventBus() != null) {

                                                vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                                        .put("eventType", "IP_RANGE_UPDATED")
                                                        .put("eventContext", "Updated " + affected + " IP(s) in range " + sIp + " - " + eIp + " to " + normStatus + " in Subnet " + subAddr)
                                                        .put("severity", 1));

                                            }

                                            return new JsonObject().put("success", true).put("message", "Successfully updated " + affected + " IP address(es) to " + normStatus);

                                        });

                            });

                });

    }

    /**
     * Resets an IP range to AVAILABLE within a subnet.
     */
    public Future<JsonObject> deleteIpRange(Long subnetId, String startIp, String endIp, String username) {

        return updateIpRangeStatus(subnetId, startIp, endIp, "AVAILABLE", username)
                .map(r -> new JsonObject().put("success", true).put("message", "Selected IP range removed and reset to Available"));

    }

    /**
     * Resets multiple selected IP IDs to AVAILABLE.
     */
    public Future<JsonObject> deleteMultipleIps(List<Long> ipIds, String username) {

        if (ipIds == null || ipIds.isEmpty()) {

            return Future.failedFuture("No IP IDs provided for deletion");

        }

        String user = (username != null && !username.isBlank()) ? username : "System";

        StringBuilder placeholders = new StringBuilder();

        List<Object> params = new ArrayList<>();

        params.add("AVAILABLE");

        for (int i = 0; i < ipIds.size(); i++) {

            if (i > 0) {

                placeholders.append(",");

            }

            placeholders.append("?");

            params.add(ipIds.get(i));

        }

        String updateSql = "UPDATE subnet_ip_details SET previous_status = status, status = ? WHERE id IN (" + placeholders + ")";

        return mysqlPool.preparedQuery(updateSql).execute(Tuple.from(params))
                .compose(r -> {

                    String logMsg = "IP reset to AVAILABLE via bulk delete by " + user;

                    String logSql = "INSERT INTO ip_change_log (user, ip_address_id, subnet_id, ip, timestamp, changelog) " +
                            "SELECT ?, id, subnet_id_id, ip_address, NOW(), ? " +
                            "FROM subnet_ip_details WHERE id IN (" + placeholders + ")";

                    List<Object> logParams = new ArrayList<>();

                    logParams.add(user);

                    logParams.add(logMsg);

                    logParams.addAll(ipIds);

                    return mysqlPool.preparedQuery(logSql).execute(Tuple.from(logParams));

                })
                .compose(r -> mysqlPool.preparedQuery(DbQueries.SYNC_ALL_SUBNET_IP_COUNTS).execute())
                .map(r -> {

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "IPS_DELETED")
                                .put("eventContext", "Deleted/reset " + ipIds.size() + " IP addresses to AVAILABLE by " + user)
                                .put("severity", 2));

                    }

                    return new JsonObject().put("success", true).put("message", "Selected IP addresses deleted successfully");

                });

    }

}

