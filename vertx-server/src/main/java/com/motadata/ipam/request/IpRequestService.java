package com.motadata.ipam.request;


import com.motadata.ipam.core.database.DbQueries;
import com.motadata.ipam.core.database.DbUtil;
import com.motadata.ipam.event.EventService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-blocking Reactive IP Request Service.
 * Handles IP address requests, status queries, approvals, rejections,
 * IP reservation in subnet_ip_details, IP change logging, and audit events.
 */
public class IpRequestService {

    private static final Logger logger = LoggerFactory.getLogger(IpRequestService.class);

    private final MySQLPool mysqlPool;
    private final Vertx vertx;

    public IpRequestService(MySQLPool mysqlPool, Vertx vertx) {
        this.mysqlPool = mysqlPool;
        this.vertx = vertx;
    }

    /**
     * Lists IP requests.
     * If isAdmin is true, lists all requests. Otherwise lists requests created by the user.
     */
    public Future<JsonArray> listRequests(String username, boolean isAdmin) {
        String sql = isAdmin ? DbQueries.LIST_ALL_IP_REQUESTS : DbQueries.LIST_USER_IP_REQUESTS;
        Tuple params = isAdmin ? Tuple.tuple() : Tuple.of(username);

        return mysqlPool.preparedQuery(sql).execute(params)
                .map(rows -> {
                    JsonArray result = new JsonArray();
                    for (Row row : rows) {
                        result.add(mapRowToRequest(row));
                    }
                    return result;
                });
    }

    /**
     * Retrieves a single IP request by ID.
     */
    public Future<JsonObject> getRequestById(Long id) {
        if (id == null || id <= 0) {
            return Future.failedFuture("Invalid IP request ID");
        }

        return mysqlPool.preparedQuery(DbQueries.FIND_IP_REQUEST_BY_ID)
                .execute(Tuple.of(id))
                .compose(rows -> {
                    if (!rows.iterator().hasNext()) {
                        return Future.failedFuture("IP Request not found with ID: " + id);
                    }
                    Row row = rows.iterator().next();
                    JsonObject request = mapRowToRequest(row);

                    String subnetIdStr = request.getString("subnetId");
                    if (subnetIdStr != null && !subnetIdStr.isEmpty() && !"null".equalsIgnoreCase(subnetIdStr)) {
                        try {
                            Long subnetId = Long.parseLong(subnetIdStr);
                            return mysqlPool.preparedQuery(DbQueries.GET_SUBNET_BY_ID)
                                    .execute(Tuple.of(subnetId))
                                    .map(subnetRows -> {
                                        if (subnetRows.iterator().hasNext()) {
                                            Row sRow = subnetRows.iterator().next();
                                            request.put("subnetAddress", DbUtil.getString(sRow, "subnetAddress"));
                                            request.put("subnetCidr", DbUtil.getInt(sRow, "subnetCidr"));
                                        }
                                        return request;
                                    })
                                    .recover(err -> Future.succeededFuture(request));
                        } catch (NumberFormatException ignored) {}
                    }
                    return Future.succeededFuture(request);
                });
    }

    /**
     * Creates a new IP request.
     */
    public Future<JsonObject> createRequest(JsonObject body, String username) {
        if (body == null) {
            return Future.failedFuture("Request body cannot be empty");
        }

        int numberOfIps = body.getInteger("numberOfIps", body.getInteger("NoOfIps", 0));
        if (numberOfIps <= 0) {
            return Future.failedFuture("Please specify a valid number of IPs (greater than 0)");
        }

        String purpose = body.getString("purpose", "General allocation");
        boolean preferredSubnet = Boolean.TRUE.equals(body.getBoolean("preferredSubnet", false));
        String subnetId = body.getString("subnetId");
        if (subnetId == null && body.getValue("subnetId") != null) {
            subnetId = String.valueOf(body.getValue("subnetId"));
        }

        JsonArray ipsArray = body.getJsonArray("ips");
        if (ipsArray == null) {
            ipsArray = new JsonArray();
        }

        if (preferredSubnet) {
            if (ipsArray.isEmpty() || subnetId == null || subnetId.isEmpty() || "null".equalsIgnoreCase(subnetId)) {
                return Future.failedFuture("Please select a subnet and allocate IPs for preferred subnet requests");
            }
            if (ipsArray.size() != numberOfIps) {
                return Future.failedFuture("Selected IPs count (" + ipsArray.size() + ") must match requested count (" + numberOfIps + ")");
            }
        }

        String ipsJson = ipsArray.encode();
        String finalSubnetId = (subnetId != null && !"null".equalsIgnoreCase(subnetId)) ? subnetId : null;
        String finalUsername = (username != null && !username.isEmpty()) ? username : "System";

        Tuple params = Tuple.of(
                numberOfIps,
                ipsJson,
                finalSubnetId,
                preferredSubnet,
                purpose,
                finalUsername
        );

        return mysqlPool.preparedQuery(DbQueries.INSERT_IP_REQUEST)
                .execute(params)
                .map(rows -> {
                    Long generatedId = rows.property(io.vertx.mysqlclient.MySQLClient.LAST_INSERTED_ID);

                    if (vertx != null && vertx.eventBus() != null) {
                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "IP_REQUEST_CREATED")
                                .put("eventContext", "User '" + finalUsername + "' submitted IP Request #" + generatedId + " for " + numberOfIps + " IP(s)")
                                .put("severity", 3));
                    }

                    return new JsonObject()
                            .put("id", generatedId)
                            .put("message", "IP Request has been added successfully.");
                });
    }

    /**
     * Approves an IP request and marks assigned IPs as Reserved.
     */
    public Future<JsonObject> approveRequest(Long requestId, JsonObject body, String adminUsername) {
        if (requestId == null || requestId <= 0) {
            return Future.failedFuture("Invalid IP request ID");
        }

        return mysqlPool.preparedQuery(DbQueries.FIND_IP_REQUEST_BY_ID)
                .execute(Tuple.of(requestId))
                .compose(rows -> {
                    if (!rows.iterator().hasNext()) {
                        return Future.failedFuture("IP Request not found with ID: " + requestId);
                    }
                    Row reqRow = rows.iterator().next();
                    int status = DbUtil.getIntOrDefault(reqRow, "status", 0);
                    if (status != 0) {
                        return Future.failedFuture("IP Request is not in PENDING status");
                    }

                    int requestedCount = DbUtil.getIntOrDefault(reqRow, "numberOfIps", 0);
                    boolean isPreferred = Boolean.TRUE.equals(DbUtil.getBoolean(reqRow, "preferredSubnet"));

                    String remark = body != null ? body.getString("remark", "") : "";
                    String subnetId = body != null ? body.getString("subnetId") : null;
                    if (subnetId == null && body != null && body.getValue("subnetId") != null) {
                        subnetId = String.valueOf(body.getValue("subnetId"));
                    }
                    if (subnetId == null || subnetId.isEmpty() || "null".equalsIgnoreCase(subnetId)) {
                        subnetId = DbUtil.getString(reqRow, "subnetId");
                    }

                    JsonArray ipsArray = body != null ? body.getJsonArray("ips") : null;
                    if ((ipsArray == null || ipsArray.isEmpty()) && isPreferred) {
                        String storedIps = DbUtil.getString(reqRow, "ips");
                        ipsArray = parseIpsJson(storedIps);
                    }

                    if (ipsArray == null || ipsArray.isEmpty()) {
                        return Future.failedFuture("Please allocate IPs before approving the request");
                    }

                    if (ipsArray.size() != requestedCount) {
                        return Future.failedFuture("Selected IPs count (" + ipsArray.size() + ") must match requested count (" + requestedCount + ")");
                    }

                    List<String> ipList = new ArrayList<>();
                    for (int i = 0; i < ipsArray.size(); i++) {
                        ipList.add(ipsArray.getString(i));
                    }

                    String finalAdmin = (adminUsername != null && !adminUsername.isEmpty()) ? adminUsername : "admin";
                    String finalSubnetId = subnetId;
                    String ipsJson = ipsArray.encode();

                    return applyApproval(requestId, finalSubnetId, ipsJson, remark, finalAdmin, ipList);
                });
    }

    private Future<JsonObject> applyApproval(Long requestId, String subnetId, String ipsJson, String remark, String adminUsername, List<String> ipList) {
        return mysqlPool.preparedQuery(DbQueries.APPROVE_IP_REQUEST)
                .execute(Tuple.of(subnetId, ipsJson, remark, adminUsername, requestId))
                .compose(updateRes -> {
                    List<Future<Void>> ipUpdates = new ArrayList<>();
                    for (String ip : ipList) {
                        ipUpdates.add(updateIpStatusAndLogChange(ip, adminUsername));
                    }

                    return Future.all(new ArrayList<>(ipUpdates))
                            .map(v -> {
                                if (vertx != null && vertx.eventBus() != null) {
                                    vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                            .put("eventType", "IP_REQUEST_APPROVED")
                                            .put("eventContext", "Admin '" + adminUsername + "' approved IP Request #" + requestId + " with IPs: " + ipList)
                                            .put("severity", 3));
                                }
                                return new JsonObject()
                                        .put("id", requestId)
                                        .put("message", "IP Request has been approved successfully.");
                            });
                });
    }

    private Future<Void> updateIpStatusAndLogChange(String ip, String adminUsername) {
        return mysqlPool.preparedQuery(DbQueries.UPDATE_IP_STATUS_RESERVED)
                .execute(Tuple.of(ip))
                .compose(res -> mysqlPool.preparedQuery(DbQueries.FIND_IP_DETAILS_BY_IP).execute(Tuple.of(ip)))
                .compose(ipRows -> {
                    if (ipRows.iterator().hasNext()) {
                        Row row = ipRows.iterator().next();
                        Long ipId = DbUtil.getLong(row, "id");
                        Long subnetId = DbUtil.getLong(row, "subnetId");
                        return mysqlPool.preparedQuery(DbQueries.INSERT_IP_CHANGE_LOG)
                                .execute(Tuple.of(adminUsername, ipId, subnetId != null ? subnetId : 1L, ip))
                                .map(r -> (Void) null);
                    }
                    return Future.<Void>succeededFuture(null);
                })
                .recover(err -> {
                    logger.warn("Could not log IP change log for {}: {}", ip, err.getMessage());
                    return Future.<Void>succeededFuture(null);
                });
    }

    /**
     * Rejects an IP request.
     */
    public Future<JsonObject> rejectRequest(Long requestId, String remark, String adminUsername) {
        if (requestId == null || requestId <= 0) {
            return Future.failedFuture("Invalid IP request ID");
        }

        String finalAdmin = (adminUsername != null && !adminUsername.isEmpty()) ? adminUsername : "admin";
        String finalRemark = (remark != null) ? remark : "Request rejected by administrator";

        return mysqlPool.preparedQuery(DbQueries.REJECT_IP_REQUEST)
                .execute(Tuple.of(finalRemark, finalAdmin, requestId))
                .map(rows -> {
                    if (rows.rowCount() == 0) {
                        throw new RuntimeException("IP Request not found with ID: " + requestId);
                    }

                    if (vertx != null && vertx.eventBus() != null) {
                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "IP_REQUEST_REJECTED")
                                .put("eventContext", "Admin '" + finalAdmin + "' rejected IP Request #" + requestId + " (Remark: " + finalRemark + ")")
                                .put("severity", 2));
                    }

                    return new JsonObject()
                            .put("id", requestId)
                            .put("message", "IP Request has been rejected successfully.");
                });
    }

    private JsonObject mapRowToRequest(Row row) {
        JsonObject obj = new JsonObject();
        obj.put("id", DbUtil.getLong(row, "id"));
        obj.put("numberOfIps", DbUtil.getIntOrDefault(row, "numberOfIps", 0));
        obj.put("NoOfIps", DbUtil.getIntOrDefault(row, "numberOfIps", 0));
        obj.put("purpose", DbUtil.getString(row, "purpose"));
        obj.put("remark", DbUtil.getString(row, "remark"));
        obj.put("subnetId", DbUtil.getString(row, "subnetId"));
        obj.put("preferredSubnet", Boolean.TRUE.equals(DbUtil.getBoolean(row, "preferredSubnet")));
        obj.put("createdBy", DbUtil.getString(row, "createdBy"));
        obj.put("lastModifiedBy", DbUtil.getString(row, "lastModifiedBy"));

        int statusInt = DbUtil.getIntOrDefault(row, "status", 0);
        obj.put("status", mapStatusToString(statusInt));

        String ipsRaw = DbUtil.getString(row, "ips");
        obj.put("ips", parseIpsJson(ipsRaw));

        LocalDateTime createdDate = row.getLocalDateTime("createdDate");
        if (createdDate != null) {
            obj.put("createdDate", toDateArray(createdDate));
            obj.put("createdDateIso", createdDate.toString());
        }

        LocalDateTime lastModifiedDate = row.getLocalDateTime("lastModifiedDate");
        if (lastModifiedDate != null) {
            obj.put("lastModifiedDate", toDateArray(lastModifiedDate));
            obj.put("lastModifiedDateIso", lastModifiedDate.toString());
        }

        return obj;
    }

    private JsonArray parseIpsJson(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new JsonArray();
        }
        raw = raw.trim();
        if (raw.startsWith("[") && raw.endsWith("]")) {
            try {
                return new JsonArray(raw);
            } catch (Exception ignored) {}
        }
        JsonArray arr = new JsonArray();
        String[] parts = raw.split(",");
        for (String p : parts) {
            if (!p.trim().isEmpty()) {
                arr.add(p.trim());
            }
        }
        return arr;
    }

    private JsonArray toDateArray(LocalDateTime ldt) {
        return new JsonArray()
                .add(ldt.getYear())
                .add(ldt.getMonthValue())
                .add(ldt.getDayOfMonth())
                .add(ldt.getHour())
                .add(ldt.getMinute())
                .add(ldt.getSecond());
    }

    private String mapStatusToString(int status) {
        switch (status) {
            case 1:
                return "APPROVED";
            case 2:
                return "REJECTED";
            case 0:
            default:
                return "PENDING";
        }
    }
}
