package com.motadata.ipam.alert;

import com.motadata.ipam.core.model.ApiResponse;
import com.motadata.ipam.security.SecurityUtil;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
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
    private final Vertx vertx;

    public AlertHandler(AlertService alertService, Vertx vertx) {
        this.alertService = alertService;
        this.vertx = vertx;
    }

    /**
     * GET /api/alerts/stream
     * Server-Sent Events (SSE) stream for real-time alert push across all pages.
     */
    public void streamAlerts(RoutingContext ctx) {
        HttpServerResponse response = ctx.response();
        response.putHeader("Content-Type", "text/event-stream")
                .putHeader("Cache-Control", "no-cache")
                .putHeader("Connection", "keep-alive")
                .setChunked(true);

        response.write("event: connected\ndata: {\"status\":\"CONNECTED\"}\n\n");

        MessageConsumer<JsonObject> alertConsumer = vertx.eventBus().consumer(AlertService.ADDRESS_ALERT_STREAM, msg -> {
            JsonObject body = msg.body();
            if (body != null && !response.closed()) {
                response.write("event: alert\ndata: " + body.encode() + "\n\n");
            }
        });

        MessageConsumer<JsonObject> clearConsumer = vertx.eventBus().consumer("ipam.alert.cleared", msg -> {
            JsonObject body = msg.body();
            if (body != null && !response.closed()) {
                response.write("event: alert_cleared\ndata: " + body.encode() + "\n\n");
            }
        });

        response.closeHandler(v -> {
            alertConsumer.unregister();
            clearConsumer.unregister();
        });
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

    /**
     * GET /api/alerts/config
     * Retrieves current alert configuration rules and thresholds.
     */
    public void getConfig(RoutingContext ctx) {

        alertService.getAlertConfiguration()
                .onSuccess(config -> ApiResponse.sendSuccess(ctx, config))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "ALERT_CONFIG_FETCH_FAILED", err.getMessage()));

    }

    /**
     * PUT /api/alerts/config
     * Updates alert configuration rules and thresholds.
     */
    public void updateConfig(RoutingContext ctx) {

        JsonObject body = ctx.body().asJsonObject();

        if (body == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Request body is required");

            return;

        }

        alertService.updateAlertConfiguration(body)
                .onSuccess(updated -> ApiResponse.sendSuccess(ctx, 200, "Alert configuration updated successfully", updated))
                .onFailure(err -> ApiResponse.sendError(ctx, 400, "ALERT_CONFIG_UPDATE_FAILED", err.getMessage()));

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
