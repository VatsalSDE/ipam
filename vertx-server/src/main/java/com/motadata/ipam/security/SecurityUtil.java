package com.motadata.ipam.security;

import com.motadata.ipam.core.model.ApiResponse;

import io.vertx.ext.web.RoutingContext;

/**
 * SecurityUtil provides centralized defense against HTTP header vulnerabilities
 * and path parameter tampering.
 */
public class SecurityUtil {

    private SecurityUtil() {

        // Prevent instantiation

    }

    /**
     * Injects standard enterprise security headers into every HTTP response.
     */
    public static void applySecurityHeaders(RoutingContext ctx) {

        ctx.response()
                .putHeader("X-Content-Type-Options", "nosniff")
                .putHeader("X-Frame-Options", "DENY")
                .putHeader("X-XSS-Protection", "1; mode=block")
                .putHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
                .putHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        ctx.next();

    }

    /**
     * Safely parses and validates positive numeric IDs from URL path parameters.
     * Rejects SQL injection payloads, negative numbers, and non-numeric characters.
     */
    public static Long parseSafePositiveId(RoutingContext ctx, String paramName) {

        String rawId = ctx.pathParam(paramName);

        if (rawId == null || rawId.trim().isEmpty()) {

            ApiResponse.sendError(ctx, 400, "INVALID_ID", "Missing required parameter: " + paramName);

            return null;

        }

        try {

            long id = Long.parseLong(rawId.trim());

            if (id <= 0) {

                ApiResponse.sendError(ctx, 400, "INVALID_ID", "Parameter " + paramName + " must be a positive integer");

                return null;

            }

            return id;

        } catch (NumberFormatException e) {

            ApiResponse.sendError(ctx, 400, "INVALID_ID", "Parameter " + paramName + " must be a numeric integer");

            return null;

        }

    }

}
