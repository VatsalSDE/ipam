package com.motadata.ipam.subnet;


import com.motadata.ipam.core.model.ApiResponse;
import com.motadata.ipam.security.SecurityUtil;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

/**
 * Production-grade Subnet REST API Handler.
 * Bridges HTTP requests with SubnetService, sanitizes inputs, and produces
 * standardized ApiResponse JSON envelopes.
 */
public class SubnetHandler {

    private static final Logger logger = LoggerFactory.getLogger(SubnetHandler.class);

    private final SubnetService subnetService;

    public SubnetHandler(SubnetService subnetService) {

        this.subnetService = subnetService;

    }

    /**
     * GET /api/subnet
     * Lists subnets with pagination, search, and calculated utilization metrics.
     */
    public void list(RoutingContext ctx) {

        int limit = parseQueryParam(ctx, "limit", 50);

        int offset = parseQueryParam(ctx, "offset", 0);

        String search = ctx.request().getParam("search");

        subnetService.listSubnets(limit, offset, search)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, result))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "SUBNET_LIST_FAILED", err.getMessage()));

    }

    /**
     * GET /api/subnet/:id
     * Returns details for a single subnet + real-time IP status breakdown.
     */
    public void getById(RoutingContext ctx) {

        Long subnetId = SecurityUtil.parseSafePositiveId(ctx, "id");

        if (subnetId == null) {

            return;

        }

        subnetService.getSubnetById(subnetId)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, result))
                .onFailure(err -> {

                    if (err.getMessage() != null && err.getMessage().contains("not found")) {

                        ApiResponse.sendError(ctx, 404, "NOT_FOUND", err.getMessage());

                    } else {

                        ApiResponse.sendError(ctx, 500, "SUBNET_FETCH_FAILED", err.getMessage());

                    }

                });

    }

    /**
     * POST /api/subnet
     * Creates a new subnet, validates CIDR, checks overlaps, and batch-inserts IP records.
     */
    public void create(RoutingContext ctx) {

        JsonObject body = ctx.body().asJsonObject();

        if (body == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Request body is required");

            return;

        }

        subnetService.createSubnet(body)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, 201, result))
                .onFailure(err -> {

                    String msg = err.getMessage() != null ? err.getMessage() : "Subnet creation failed";

                    if (msg.contains("overlaps") || msg.contains("already exists") || msg.contains("required") || msg.contains("Invalid")) {

                        ApiResponse.sendError(ctx, 400, "BAD_REQUEST", msg);

                    } else {

                        ApiResponse.sendError(ctx, 500, "SUBNET_CREATE_FAILED", msg);

                    }

                });

    }

    /**
     * POST /api/subnet/check
     * Instant validation endpoint for the UI to check if a CIDR overlaps before creating.
     */
    public void check(RoutingContext ctx) {

        JsonObject body = ctx.body().asJsonObject();

        if (body == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Request body is required");

            return;

        }

        String rawAddress = body.getString("subnetAddress");

        Integer cidr = body.getInteger("subnetCidr");

        String maskInfo = body.getString("maskInfo");

        if (maskInfo != null && maskInfo.contains("/")) {

            try {

                cidr = Integer.parseInt(maskInfo.split("/")[1].trim());

            } catch (Exception ignored) {}

        }

        if (rawAddress == null || !IPv4Util.isValidIpv4(rawAddress)) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Valid subnet address is required");

            return;

        }

        if (cidr == null || cidr < 8 || cidr > 30) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "CIDR prefix must be between 8 and 30");

            return;

        }

        long inputIpLong = IPv4Util.ipToLong(rawAddress);

        long networkLong = IPv4Util.getNetworkAddress(inputIpLong, cidr);

//        long broadcastLong = IPv4Util.getBroadcastAddress(networkLong, cidr);

        String normalizedAddress = IPv4Util.longToIp(networkLong);

        String subnetMask = IPv4Util.prefixToMaskString(cidr);

        long totalIp = IPv4Util.getUsableHostCount(cidr);

        JsonObject checkResult = new JsonObject();

        checkResult.put("normalizedAddress", normalizedAddress);

        checkResult.put("subnetMask", subnetMask);

        checkResult.put("totalIp", totalIp);

        checkResult.put("isValid", true);

        ApiResponse.sendSuccess(ctx, checkResult);

    }

    /**
     * GET /api/subnet/:id/ips
     * Returns paginated IP address records for a given subnet.
     */
    public void getIps(RoutingContext ctx) {

        Long subnetId = SecurityUtil.parseSafePositiveId(ctx, "id");

        if (subnetId == null) {

            return;

        }

        int limit = parseQueryParam(ctx, "limit", 50);

        int offset = parseQueryParam(ctx, "offset", 0);

        String status = ctx.request().getParam("status");

        String search = ctx.request().getParam("search");

        subnetService.getSubnetIps(subnetId, limit, offset, status, search)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, result))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "IP_FETCH_FAILED", err.getMessage()));

    }

    /**
     * DELETE /api/subnet/:id
     * Cascades deletion of a subnet and its associated IP records.
     */
    public void delete(RoutingContext ctx) {

        Long subnetId = SecurityUtil.parseSafePositiveId(ctx, "id");

        if (subnetId == null) {

            return;

        }

        subnetService.deleteSubnet(subnetId)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, result))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "SUBNET_DELETE_FAILED", err.getMessage()));

    }

    /**
     * GET /api/subnet/ip/:id
     * Returns detailed IP record by IP record ID.
     */
    public void getIpDetails(RoutingContext ctx) {

        Long ipId = SecurityUtil.parseSafePositiveId(ctx, "id");

        if (ipId == null) {

            return;

        }

        subnetService.getIpDetails(ipId)
                .onSuccess(data -> {

                    if (data == null) {

                        ApiResponse.sendError(ctx, 404, "NOT_FOUND", "IP record not found");

                    } else {

                        ApiResponse.sendSuccess(ctx, data);

                    }

                })
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "INTERNAL_SERVER_ERROR", err.getMessage()));

    }

    /**
     * GET /api/subnet/ip/:id/changelog
     * Returns audit change logs specifically for an individual IP record ID.
     */
    public void getIpChangeLogs(RoutingContext ctx) {

        Long ipId = SecurityUtil.parseSafePositiveId(ctx, "id");

        if (ipId == null) {

            return;

        }

        int limit = parseQueryParam(ctx, "limit", 50);

        subnetService.getIpChangeLogs(ipId, limit)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, result))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "IP_CHANGELOG_FETCH_FAILED", err.getMessage()));

    }

    /**
     * POST /api/subnet/ip/range/status

     * Updates status of an IP range (e.g. from Available to Used, Transient, Reserved).
     */
    public void updateIpRangeStatus(RoutingContext ctx) {

        JsonObject body = ctx.body().asJsonObject();

        Long subnetId = null;

        String startIp = null;

        String endIp = null;

        String status = null;

        if (body != null) {

            subnetId = body.getLong("subnetId");

            startIp = body.getString("startIp");

            endIp = body.getString("endIp");

            status = body.getString("status");

        } else {

            String sidStr = ctx.request().getFormAttribute("subnetId");

            startIp = ctx.request().getFormAttribute("startIp");

            endIp = ctx.request().getFormAttribute("endIp");

            status = ctx.request().getFormAttribute("status");

            try {

                if (sidStr != null) {

                    subnetId = Long.parseLong(sidStr.trim());

                }

            } catch (Exception ignored) {}

        }

        if (subnetId == null || startIp == null || endIp == null || status == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "subnetId, startIp, endIp, and status are required");

            return;

        }

        String username = extractUsername(ctx);

        subnetService.updateIpRangeStatus(subnetId, startIp, endIp, status, username)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, result))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "IP_RANGE_UPDATE_FAILED", err.getMessage()));

    }

    /**
     * POST /api/subnet/ip/range/delete
     * Resets an IP range to Available.
     */
    public void deleteIpRange(RoutingContext ctx) {

        JsonObject body = ctx.body().asJsonObject();

        Long subnetId = null;

        String startIp = null;

        String endIp = null;

        if (body != null) {

            subnetId = body.getLong("subnetId");

            startIp = body.getString("startIp");

            endIp = body.getString("endIp");

        } else {

            String sidStr = ctx.request().getFormAttribute("subnetId");

            startIp = ctx.request().getFormAttribute("startIp");

            endIp = ctx.request().getFormAttribute("endIp");

            try {

                if (sidStr != null) {

                    subnetId = Long.parseLong(sidStr.trim());

                }

            } catch (Exception ignored) {}

        }

        if (subnetId == null || startIp == null || endIp == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "subnetId, startIp, and endIp are required");

            return;

        }

        String username = extractUsername(ctx);

        subnetService.deleteIpRange(subnetId, startIp, endIp, username)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, result))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "IP_RANGE_DELETE_FAILED", err.getMessage()));

    }

    /**
     * DELETE /api/subnet/ips
     * Resets selected IP IDs to Available.
     */
    public void deleteMultipleIps(RoutingContext ctx) {

        String idsParam = ctx.request().getParam("ids");

        java.util.List<Long> ipIds = new java.util.ArrayList<>();

        if (idsParam != null && !idsParam.isBlank()) {

            for (String part : idsParam.split(",")) {

                try {
                    ipIds.add(Long.parseLong(part.trim()));

                } catch (Exception ignored) {}

            }

        } else {

            JsonObject body = ctx.body().asJsonObject();

            if (body != null && body.containsKey("ids")) {

                io.vertx.core.json.JsonArray arr = body.getJsonArray("ids");

                for (int i = 0; i < arr.size(); i++) {

                    ipIds.add(arr.getLong(i));

                }

            }

        }

        if (ipIds.isEmpty()) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "No valid IP IDs provided");

            return;

        }

        String username = extractUsername(ctx);

        subnetService.deleteMultipleIps(ipIds, username)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, result))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "BULK_IP_DELETE_FAILED", err.getMessage()));

    }

    private String extractUsername(RoutingContext ctx) {

        if (ctx.user() != null && ctx.user().principal() != null) {

            String username = ctx.user().principal().getString("username");

            if (username != null && !username.isEmpty()) {

                return username;

            }

            String sub = ctx.user().principal().getString("sub");

            if (sub != null && !sub.isEmpty()) {

                return sub;

            }

        }

        return "System";

    }

    private int parseQueryParam(RoutingContext ctx, String paramName, int defaultValue) {

        String value = ctx.request().getParam(paramName);

        if (value == null || value.isBlank()) {

            return defaultValue;

        }

        try {

            int parsed = Integer.parseInt(value.trim());

            return parsed >= 0 ? parsed : defaultValue;

        } catch (NumberFormatException e) {

            return defaultValue;

        }

    }

}
