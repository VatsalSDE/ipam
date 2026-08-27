package com.motadata.ipam.security;

import com.motadata.ipam.model.ApiResponse;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import java.util.regex.Pattern;

/**
 * SecurityUtil provides centralized defense against IDOR (Insecure Direct Object References),
 * SQL Injection, parameter tampering, and clickjacking/MIME-sniffing attacks.
 */
public class SecurityUtil {

    private static final Pattern SAFE_SQL_IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_]+$");

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
     * Prevents IDOR (Broken Object Level Authorization).
     * Ensures that non-admin users can ONLY access/modify resources they own.
     */
    public static boolean requireOwnershipOrAdmin(RoutingContext ctx, Long resourceOwnerId) {

        io.vertx.core.json.JsonObject currentUser = ctx.get("currentUser");

        if (currentUser == null) {

            ApiResponse.sendError(ctx, 401, "UNAUTHORIZED", "Authentication required");

            return false;

        }

        Long roleId = currentUser.getLong("roleId");

        String roleName = currentUser.getString("roleName");

        boolean isAdmin = (roleId != null && roleId == 1L) || "ROLE_ADMIN".equalsIgnoreCase(roleName) || "ADMIN".equalsIgnoreCase(roleName);

        if (isAdmin) {

            return true;

        }

        Long userId = currentUser.getLong("id");

        if (userId == null) {

            userId = currentUser.getLong("userId");

        }

        if (resourceOwnerId != null && resourceOwnerId.equals(userId)) {

            return true;

        }

        // Reject unauthorized access attempts
        ApiResponse.sendError(ctx, 403, "FORBIDDEN_OBJECT_ACCESS", "Access denied: You do not have permission to access this resource.");

        return false;

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

    /**
     * Sanitizes dynamic column names for ORDER BY / GROUP BY clauses to prevent SQL injection.
     */
    public static boolean isValidSqlIdentifier(String identifier) {

        if (identifier == null || identifier.trim().isEmpty()) {

            return false;

        }

        return SAFE_SQL_IDENTIFIER.matcher(identifier.trim()).matches();

    }

}
