package com.motadata.ipam.rogue;


import com.motadata.ipam.core.model.ApiResponse;
import com.motadata.ipam.security.SecurityUtil;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

/**
 * REST API Handler for Rogue Device Detection & Whitelisting.
 */
public class RogueDetectionHandler {

    private static final Logger logger = LoggerFactory.getLogger(RogueDetectionHandler.class);

    private final RogueDetectionService rogueDetectionService;

    public RogueDetectionHandler(RogueDetectionService rogueDetectionService) {

        this.rogueDetectionService = rogueDetectionService;

    }

    /**
     * GET /api/rogue-detection
     * Lists paginated discovered and rogue devices.
     */
    public void list(RoutingContext ctx) {

        int limit = parseQueryParam(ctx, "limit", 50);

        int offset = parseQueryParam(ctx, "offset", 0);

        rogueDetectionService.listRogueDevices(limit, offset)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, result))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "ROGUE_LIST_FAILED", err.getMessage()));

    }

    /**
     * POST /api/rogue-detection
     * Registers a new discovered / rogue MAC.
     */
    public void create(RoutingContext ctx) {

        JsonObject body = ctx.body().asJsonObject();

        if (body == null || body.getString("macAddress") == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "macAddress is required");

            return;

        }

        String mac = body.getString("macAddress");

        String ip = body.getString("ipAddress", "");

        String nicType = body.getString("nicType", "Ethernet");

        String authenticity = body.getString("authenticity", "DISCOVERED");

        rogueDetectionService.addRogueDevice(mac, ip, nicType, authenticity)
                .onSuccess(v -> ApiResponse.sendSuccess(ctx, 201, new JsonObject().put("message", "Device registered successfully")))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "ROGUE_CREATE_FAILED", err.getMessage()));

    }

    /**
     * PUT /api/rogue-detection/:id/authenticity
     * Updates device status to TRUSTED, ROGUE, or DISCOVERED.
     */
    public void updateAuthenticity(RoutingContext ctx) {

        Long id = SecurityUtil.parseSafePositiveId(ctx, "id");

        if (id == null) {

            return;

        }

        JsonObject body = ctx.body().asJsonObject();

        String auth = body != null ? body.getString("authenticity") : null;

        if (auth == null || auth.isBlank()) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "authenticity status is required (TRUSTED/ROGUE/DISCOVERED)");

            return;

        }

        rogueDetectionService.updateAuthenticity(id, auth)
                .onSuccess(v -> ApiResponse.sendSuccess(ctx, new JsonObject().put("message", "Authenticity updated to " + auth.toUpperCase())))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "AUTHENTICITY_UPDATE_FAILED", err.getMessage()));

    }

    /**
     * DELETE /api/rogue-detection/:id
     * Deletes a rogue device record.
     */
    public void delete(RoutingContext ctx) {

        Long id = SecurityUtil.parseSafePositiveId(ctx, "id");

        if (id == null) {

            return;

        }

        rogueDetectionService.deleteRogueDevice(id)
                .onSuccess(v -> ApiResponse.sendSuccess(ctx, new JsonObject().put("message", "Device deleted successfully")))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "ROGUE_DELETE_FAILED", err.getMessage()));

    }

    private int parseQueryParam(RoutingContext ctx, String param, int defaultValue) {

        String val = ctx.request().getParam(param);

        if (val == null || val.isBlank()) {

            return defaultValue;

        }

        try {

            return Integer.parseInt(val.trim());

        } catch (NumberFormatException e) {

            return defaultValue;

        }

    }

}
