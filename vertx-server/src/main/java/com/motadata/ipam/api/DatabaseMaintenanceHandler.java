package com.motadata.ipam.api;

import com.motadata.ipam.model.ApiResponse;

import com.motadata.ipam.service.DatabaseMaintenanceService;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

/**
 * REST API Handler for Database Maintenance & Data Retention.
 */
public class DatabaseMaintenanceHandler {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMaintenanceHandler.class);

    private final DatabaseMaintenanceService maintenanceService;

    public DatabaseMaintenanceHandler(DatabaseMaintenanceService maintenanceService) {

        this.maintenanceService = maintenanceService;

    }

    /**
     * GET /api/database-maintenance
     * Retrieves current data retention policy settings.
     */
    public void getSettings(RoutingContext ctx) {

        maintenanceService.getSettings()
                .onSuccess(settings -> ApiResponse.sendSuccess(ctx, settings))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "SETTINGS_FETCH_FAILED", err.getMessage()));

    }

    /**
     * PUT /api/database-maintenance
     * Updates data retention policy settings.
     */
    public void updateSettings(RoutingContext ctx) {

        JsonObject body = ctx.body().asJsonObject();

        if (body == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Request body is required");

            return;

        }

        maintenanceService.updateSettings(body)
                .onSuccess(updated -> ApiResponse.sendSuccess(ctx, 200, "Database maintenance settings updated successfully", updated))
                .onFailure(err -> ApiResponse.sendError(ctx, 400, "SETTINGS_UPDATE_FAILED", err.getMessage()));

    }

    /**
     * DELETE /api/database-maintenance or POST /api/database-maintenance/purge
     * Triggers immediate data retention purge of expired records.
     */
    public void purge(RoutingContext ctx) {

        int days = 30;

        String daysParam = ctx.request().getParam("days");

        if (daysParam != null && !daysParam.isBlank()) {

            try {

                days = Integer.parseInt(daysParam.trim());

            } catch (NumberFormatException ignored) {

            }

        } else if (ctx.body().asJsonObject() != null && ctx.body().asJsonObject().containsKey("maintainedDays")) {

            days = ctx.body().asJsonObject().getInteger("maintainedDays", 30);

        }

        maintenanceService.purgeOldData(days)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, 200, "Data retention archive executed successfully", result))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "PURGE_FAILED", err.getMessage()));

    }

}
