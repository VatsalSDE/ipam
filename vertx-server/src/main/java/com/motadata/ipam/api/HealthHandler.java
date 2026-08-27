package com.motadata.ipam.api;

import com.motadata.ipam.database.DatabasePool;

import com.motadata.ipam.model.ApiResponse;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import io.vertx.mysqlclient.MySQLPool;

import java.lang.management.ManagementFactory;

import java.time.Instant;

/**
 * HealthHandler provides system health and database connectivity diagnostics.
 */
public class HealthHandler {

    private final MySQLPool mysqlPool;

    private final long startTime;

    public HealthHandler(MySQLPool mysqlPool) {

        this.mysqlPool = mysqlPool;

        this.startTime = System.currentTimeMillis();

    }

    public void checkHealth(RoutingContext ctx) {

        long uptimeMs = System.currentTimeMillis() - startTime;

        JsonObject health = new JsonObject();

        health.put("status", "UP");

        health.put("product", "Motadata TraceOrg IPAM (Vert.x Core)");

        health.put("version", "4.0.0");

        health.put("uptimeMs", uptimeMs);

        health.put("jvmVersion", System.getProperty("java.version"));

        health.put("timestamp", Instant.now().toString());

        // Check Database Connectivity using modern Vert.x Future API
        if (mysqlPool != null) {

            mysqlPool.query("SELECT 1").execute()
                    .onSuccess(rows -> {

                        health.put("database", "CONNECTED");

                        ctx.response()
                                .putHeader("Content-Type", "application/json")
                                .setStatusCode(200)
                                .end(ApiResponse.success(health).encodePrettily());

                    })
                    .onFailure(err -> {

                        health.put("database", "DISCONNECTED: " + err.getMessage());

                        ctx.response()
                                .putHeader("Content-Type", "application/json")
                                .setStatusCode(200)
                                .end(ApiResponse.success("Application running with database warning", health).encodePrettily());

                    });

        } else {

            health.put("database", "NOT_CONFIGURED");

            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(ApiResponse.success(health).encodePrettily());

        }

    }

}
