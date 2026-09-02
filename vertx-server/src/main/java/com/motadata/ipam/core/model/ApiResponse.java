package com.motadata.ipam.core.model;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Standard API Response envelope across all IPAM REST endpoints.
 */
public class
ApiResponse {

    private static final Logger logger = LoggerFactory.getLogger(ApiResponse.class);

    private boolean success;

    private Object data;

    private String message;

    private String errorCode;

    private long timestamp;

    public ApiResponse() {

        this.timestamp = Instant.now().toEpochMilli();

    }

    public static JsonObject success(Object data) {

        JsonObject response = new JsonObject();

        response.put("success", true);

        response.put("data", data);

        response.put("timestamp", Instant.now().toEpochMilli());

        return response;

    }

    public static JsonObject success(String message, Object data) {

        JsonObject response = new JsonObject();

        response.put("success", true);

        response.put("message", message);

        response.put("data", data);

        response.put("timestamp", Instant.now().toEpochMilli());

        return response;

    }

    public static JsonObject error(String errorCode, String message) {

        JsonObject response = new JsonObject();

        response.put("success", false);

        response.put("errorCode", errorCode);

        response.put("message", message);

        response.put("timestamp", Instant.now().toEpochMilli());

        return response;

    }

    public static void sendSuccess(RoutingContext ctx, Object data) {

        ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(200)
                .end(success(data).encodePrettily());

    }

    public static void sendSuccess(RoutingContext ctx, int statusCode, Object data) {

        ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(statusCode)
                .end(success(data).encodePrettily());

    }

    public static void sendSuccess(RoutingContext ctx, int statusCode, String message, Object data) {

        ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(statusCode)
                .end(success(message, data).encodePrettily());

    }

    public static void sendError(RoutingContext ctx, int statusCode, String errorCode, String message) {

        ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(statusCode)
                .end(error(errorCode, message).encodePrettily());

    }

    public static void handleDatabaseError(RoutingContext ctx, Throwable error) {

        String errorMsg = error != null && error.getMessage() != null ? error.getMessage() : "Unknown database error";

        logger.error("Database operation failed for URI: {} - Error: {}", ctx.request().uri(), errorMsg, error);

        if (errorMsg.toLowerCase().contains("pool wait queue is full") || errorMsg.toLowerCase().contains("pool is full")) {

            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Retry-After", "5")
                    .setStatusCode(503)
                    .end(error("DATABASE_POOL_SATURATED",
                            "Database connection pool is temporarily saturated under high traffic. Please retry in a few seconds.").encodePrettily());

        } else if (errorMsg.toLowerCase().contains("timeout") || errorMsg.toLowerCase().contains("timed out") || errorMsg.toLowerCase().contains("connection refused")) {

            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Retry-After", "10")
                    .setStatusCode(503)
                    .end(error("DATABASE_UNAVAILABLE",
                            "Database service is temporarily unreachable or timed out.").encodePrettily());

        } else {

            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(500)
                    .end(error("DATABASE_ERROR", errorMsg).encodePrettily());

        }

    }

    public static void handleFailure(RoutingContext ctx) {

        Throwable failure = ctx.failure();

        int statusCode = ctx.statusCode() > 0 ? ctx.statusCode() : 500;

        String errorMsg = failure != null && failure.getMessage() != null ? failure.getMessage() : "Internal server error occurred";

        logger.error("Unhandled routing failure on URI: {} with status: {} - {}", ctx.request().uri(), statusCode, errorMsg, failure);

        if (failure != null && (errorMsg.toLowerCase().contains("pool wait queue is full") || errorMsg.toLowerCase().contains("pool is full"))) {

            handleDatabaseError(ctx, failure);

            return;

        }

        ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(statusCode)
                .end(error("SERVER_ERROR", errorMsg).encodePrettily());

    }

    public boolean isSuccess() {

        return success;

    }

    public void setSuccess(boolean success) {

        this.success = success;

    }

    public Object getData() {

        return data;

    }

    public void setData(Object data) {

        this.data = data;

    }

    public String getMessage() {

        return message;

    }

    public void setMessage(String message) {

        this.message = message;

    }

    public String getErrorCode() {

        return errorCode;

    }

    public void setErrorCode(String errorCode) {

        this.errorCode = errorCode;

    }

    public long getTimestamp() {

        return timestamp;

    }

}

