package com.motadata.ipam.api;

import com.motadata.ipam.model.ApiResponse;

import com.motadata.ipam.security.SecurityUtil;

import com.motadata.ipam.service.SubnetService;

import com.motadata.ipam.util.IPv4Util;

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
