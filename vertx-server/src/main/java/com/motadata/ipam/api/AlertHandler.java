package com.motadata.ipam.api;

import com.motadata.ipam.model.ApiResponse;

import com.motadata.ipam.security.SecurityUtil;

import com.motadata.ipam.service.AlertService;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

/**
 * REST API Handler for Alert Stream & Notifications.
 */
public class AlertHandler {

    private static final Logger logger = LoggerFactory.getLogger(AlertHandler.class);

    private final AlertService alertService;

    public AlertHandler(AlertService alertService) {

        this.alertService = alertService;

    }

    /**
     * GET /api/alerts
     * Lists alerts with optional activeOnly filter.
     */
    public void list(RoutingContext ctx) {

        int limit = parseQueryParam(ctx, "limit", 50);

        int offset = parseQueryParam(ctx, "offset", 0);

        String activeParam = ctx.request().getParam("activeOnly");

        boolean activeOnly = "true".equalsIgnoreCase(activeParam) || "1".equals(activeParam);

        alertService.listAlerts(limit, offset, activeOnly)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, result))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "ALERT_LIST_FAILED", err.getMessage()));

    }

    /**
     * POST /api/alerts
     * Creates and publishes an alert.
     */
    public void create(RoutingContext ctx) {

        JsonObject body = ctx.body().asJsonObject();

        if (body == null || body.getString("message") == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Message is required");

            return;

        }

        Long subnetId = body.getLong("subnetId", 0L);

        String alertType = body.getString("alertType", "GENERAL");

        String message = body.getString("message");

        String subnet = body.getString("subnet", "");

        alertService.publishAlert(subnetId, alertType, message, subnet)
                .onSuccess(v -> ApiResponse.sendSuccess(ctx, 201, new JsonObject().put("message", "Alert published successfully")))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "ALERT_PUBLISH_FAILED", err.getMessage()));

    }

    /**
     * PUT /api/alerts/:id/clear
     * Clears an active alert.
     */
    public void clear(RoutingContext ctx) {

        Long alertId = SecurityUtil.parseSafePositiveId(ctx, "id");

        if (alertId == null) {

            return;

        }

        alertService.clearAlert(alertId)
                .onSuccess(v -> ApiResponse.sendSuccess(ctx, new JsonObject().put("message", "Alert cleared successfully")))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "ALERT_CLEAR_FAILED", err.getMessage()));

    }

    /**
     * DELETE /api/alerts/:id
     * Deletes an alert record.
     */
    public void delete(RoutingContext ctx) {

        Long alertId = SecurityUtil.parseSafePositiveId(ctx, "id");

        if (alertId == null) {

            return;

        }

        alertService.deleteAlert(alertId)
                .onSuccess(v -> ApiResponse.sendSuccess(ctx, new JsonObject().put("message", "Alert deleted successfully")))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "ALERT_DELETE_FAILED", err.getMessage()));

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
