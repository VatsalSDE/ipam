package com.motadata.ipam.api;

import com.motadata.ipam.model.ApiResponse;

import com.motadata.ipam.security.SecurityUtil;

import com.motadata.ipam.service.ScannerService;

import com.motadata.ipam.verticle.ScanWorkerVerticle;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

/**
 * REST API Handler for on-demand Subnet Scanning and Status reporting.
 * Decoupled from execution via Vert.x EventBus messaging to ScanWorkerVerticle.
 */
public class ScannerHandler {

    private static final Logger logger = LoggerFactory.getLogger(ScannerHandler.class);

    private final Vertx vertx;

    private final ScannerService scannerService;

    public ScannerHandler(Vertx vertx) {

        this.vertx = vertx;

        this.scannerService = null;

    }

    public ScannerHandler(ScannerService scannerService) {

        this.vertx = null;

        this.scannerService = scannerService;

    }

    /**
     * POST /api/subnet/:id/scan
     * GET  /api/subnet/scan/:id (Legacy compatibility)
     * Triggers an asynchronous network ICMP ping scan via EventBus.
     */
    public void triggerScan(RoutingContext ctx) {

        Long subnetId = SecurityUtil.parseSafePositiveId(ctx, "id");

        if (subnetId == null) {

            return;

        }

        if (vertx != null) {

            JsonObject payload = new JsonObject().put("subnetId", subnetId);

            vertx.eventBus().<JsonObject>request(ScanWorkerVerticle.ADDRESS_SCAN_TRIGGER, payload)
                    .onSuccess(reply -> ApiResponse.sendSuccess(ctx, reply.body()))
                    .onFailure(err -> {

                        String msg = err.getMessage() != null ? err.getMessage() : "Subnet scan failed";

                        if (msg.contains("not found")) {

                            ApiResponse.sendError(ctx, 404, "NOT_FOUND", msg);

                        } else {

                            ApiResponse.sendError(ctx, 500, "SCAN_EXECUTION_FAILED", msg);

                        }

                    });

        } else if (scannerService != null) {

            scannerService.triggerScan(subnetId)
                    .onSuccess(result -> ApiResponse.sendSuccess(ctx, result))
                    .onFailure(err -> {

                        String msg = err.getMessage() != null ? err.getMessage() : "Subnet scan failed";

                        if (msg.contains("not found")) {

                            ApiResponse.sendError(ctx, 404, "NOT_FOUND", msg);

                        } else {

                            ApiResponse.sendError(ctx, 500, "SCAN_EXECUTION_FAILED", msg);

                        }

                    });

        }

    }

    /**
     * GET /api/subnet/:id/scan-status
     * Returns the active or last-known scan status for a subnet via EventBus.
     */
    public void getScanStatus(RoutingContext ctx) {

        Long subnetId = SecurityUtil.parseSafePositiveId(ctx, "id");

        if (subnetId == null) {

            return;

        }

        if (vertx != null) {

            JsonObject payload = new JsonObject().put("subnetId", subnetId);

            vertx.eventBus().<JsonObject>request(ScanWorkerVerticle.ADDRESS_SCAN_STATUS, payload)
                    .onSuccess(reply -> ApiResponse.sendSuccess(ctx, reply.body()))
                    .onFailure(err -> ApiResponse.sendError(ctx, 500, "SCAN_STATUS_FAILED", err.getMessage()));

        } else if (scannerService != null) {

            JsonObject status = scannerService.getScanStatus(subnetId);

            ApiResponse.sendSuccess(ctx, status);

        }

    }

    /**
     * GET /api/subnet/scan-status/active
     * Returns any actively running scan across the system.
     */
    public void getGlobalScanStatus(RoutingContext ctx) {

        if (vertx != null) {

            JsonObject payload = new JsonObject().put("subnetId", 0L);

            vertx.eventBus().<JsonObject>request(ScanWorkerVerticle.ADDRESS_SCAN_STATUS, payload)
                    .onSuccess(reply -> ApiResponse.sendSuccess(ctx, reply.body()))
                    .onFailure(err -> ApiResponse.sendError(ctx, 500, "SCAN_STATUS_FAILED", err.getMessage()));

        } else if (scannerService != null) {

            JsonObject status = scannerService.getAnyActiveScan();

            ApiResponse.sendSuccess(ctx, status != null ? status : new JsonObject().put("status", "IDLE"));

        }

    }

}
