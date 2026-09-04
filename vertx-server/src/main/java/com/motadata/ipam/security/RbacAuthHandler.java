package com.motadata.ipam.security;


import com.motadata.ipam.core.model.ApiResponse;

import io.vertx.core.Handler;

import io.vertx.core.http.Cookie;

import io.vertx.core.json.JsonArray;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * RbacAuthHandler provides Bearer token authentication and role/permission authorization
 * using native Vert.x JsonObject data.
 */
public class RbacAuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(RbacAuthHandler.class);

    private final JwtTokenService jwtTokenService;

    public RbacAuthHandler(JwtTokenService jwtTokenService) {

        this.jwtTokenService = jwtTokenService;

    }

    public void authenticate(RoutingContext ctx) {

        authenticateAndRun(ctx, principal -> ctx.next());

    }

    public Handler<RoutingContext> requirePermission(String permission) {

        return ctx -> {

            JsonObject user = ctx.get("currentUser");

            if (user == null) {

                authenticateAndRun(ctx, principal -> checkPermissionAndProceed(ctx, principal, permission));

                return;

            }

            checkPermissionAndProceed(ctx, user, permission);

        };

    }

    public Handler<RoutingContext> authenticateHandler() {

        return this::authenticate;

    }

    private void authenticateAndRun(RoutingContext ctx, Consumer<JsonObject> onSuccessAction) {

        String token = extractToken(ctx);

        if (token == null || token.isEmpty()) {

            ApiResponse.sendError(ctx, 401, "UNAUTHORIZED", "Authentication required: Missing or invalid Authorization Bearer token");

            return;

        }

        jwtTokenService.verifyToken(token)
                .onSuccess(principal -> {

                    ctx.put("currentUser", principal);

                    ctx.put("currentUsername", principal.getString("sub"));

                    ctx.put("currentUserId", principal.getLong("userId"));

                    onSuccessAction.accept(principal);

                })
                .onFailure(err -> {

                    logger.debug("Token authentication failed: {}", err.getMessage());

                    String msg = err.getMessage() != null ? err.getMessage().toLowerCase() : "";

                    if (msg.contains("expired") || msg.contains("expiration")) {

                        ApiResponse.sendError(ctx, 401, "TOKEN_EXPIRED", "Access token has expired. Please refresh token.");

                    } else {

                        ApiResponse.sendError(ctx, 401, "INVALID_TOKEN", "Invalid token: " + err.getMessage());

                    }

                });

    }

    private void checkPermissionAndProceed(RoutingContext ctx, JsonObject user, String permission) {

        Long roleId = user.getLong("roleId");

        String roleName = user.getString("roleName");

        // Admin has full access
        if ((roleId != null && roleId == 1L) || "ROLE_ADMIN".equalsIgnoreCase(roleName) || "ADMIN".equalsIgnoreCase(roleName)) {

            ctx.next();

            return;

        }

        JsonArray permissions = user.getJsonArray("permissions");

        String altPermission = permission != null && permission.contains("_") ? permission.replace("_", " ") : (permission != null ? permission.replace(" ", "_") : "");

        boolean hasPerm = permissions != null && (
                permissions.contains(permission) ||
                permissions.contains(altPermission) ||
                permissions.contains("ALL") ||
                permissions.contains("PERM_ALL") ||
                ("PERM_SUBNET_VIEW".equalsIgnoreCase(permission) && (permissions.contains("PERM_DASHBOARD_READ") || permissions.contains("PERM_DASHBOARD_VIEW"))) ||
                ("PERM_SUBNET_READ".equalsIgnoreCase(permission) && (permissions.contains("PERM_DASHBOARD_READ") || permissions.contains("PERM_DASHBOARD_VIEW"))) ||
                ("PERM_SUBNET_EDIT".equalsIgnoreCase(permission) && (permissions.contains("PERM_DASHBOARD_WRITE") || permissions.contains("PERM_DASHBOARD_EDIT"))) ||
                ("PERM_SUBNET_DELETE".equalsIgnoreCase(permission) && (permissions.contains("PERM_DASHBOARD_WRITE") || permissions.contains("PERM_DASHBOARD_EDIT"))) ||
                ("PERM_SUBNET_WRITE".equalsIgnoreCase(permission) && (permissions.contains("PERM_DASHBOARD_WRITE") || permissions.contains("PERM_DASHBOARD_EDIT")))
        );

        if (hasPerm) {

            ctx.next();

            return;

        } else {

            logger.warn("User '{}' denied access for missing permission: {}", user.getString("sub"), permission);

            ApiResponse.sendError(ctx, 403, "FORBIDDEN", "Insufficient permissions: Requires " + permission);

        }

    }

    private String extractToken(RoutingContext ctx) {

//        Checks 3 locations in fallback order:
//
//        1. Standard Header: Authorization: Bearer <jwt> (used by REST clients, Postman, mobile apps).
//        2. Alternative Header: accessToken: <jwt> (used by custom frontend AJAX interceptors).
//        3. Browser Cookie: Cookie: token=<jwt> (used by standard web browser navigation).


        String authHeader = ctx.request().getHeader("Authorization");

        if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {

            return authHeader.substring(7).trim();

        }

        String altToken = ctx.request().getHeader("accessToken");

        if (altToken != null && !altToken.trim().isEmpty()) {

            return altToken.trim();

        }

        Cookie cookie = ctx.request().getCookie("token");

        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isEmpty()) {

            return cookie.getValue().trim();

        }

        return null;

    }

}
