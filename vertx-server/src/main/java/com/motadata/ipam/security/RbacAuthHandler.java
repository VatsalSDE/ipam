package com.motadata.ipam.security;


import com.motadata.ipam.core.model.ApiResponse;

import io.vertx.core.Handler;

import io.vertx.core.http.Cookie;

import io.vertx.core.json.JsonArray;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

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

        String token = extractToken(ctx);

        if (token == null || token.isEmpty()) {

            ApiResponse.sendError(ctx, 401, "UNAUTHORIZED", "Missing or invalid Authorization Bearer header");

            return;

        }

        jwtTokenService.verifyToken(token)
                .onSuccess(principal -> {

                    ctx.put("currentUser", principal);

                    ctx.put("currentUsername", principal.getString("sub"));

                    ctx.put("currentUserId", principal.getLong("userId"));

                    ctx.next();

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

    public Handler<RoutingContext> requirePermission(String permission) {

        return ctx -> {

            JsonObject user = ctx.get("currentUser");

            // here have checked is the user is null because it can hapen that this handlers is called directly without the authenticate so might not gtet the context of the user so that's why

            if (user == null) {

                String token = extractToken(ctx);

                if (token == null || token.isEmpty()) {

                    ApiResponse.sendError(ctx, 401, "UNAUTHORIZED", "Authentication required");

                    return;

                }

                jwtTokenService.verifyToken(token)
                        .onSuccess(principal -> {

                            ctx.put("currentUser", principal);

                            ctx.put("currentUsername", principal.getString("sub"));

                            ctx.put("currentUserId", principal.getLong("userId"));

                            checkPermissionAndProceed(ctx, principal, permission);

                        })
                        .onFailure(err -> {

                            String msg = err.getMessage() != null ? err.getMessage().toLowerCase() : "";

                            if (msg.contains("expired") || msg.contains("expiration")) {

                                ApiResponse.sendError(ctx, 401, "TOKEN_EXPIRED", "Access token has expired. Please refresh token.");

                            } else {

                                ApiResponse.sendError(ctx, 401, "INVALID_TOKEN", "Token validation failed: " + err.getMessage());

                            }

                        });

                return;

            }

            checkPermissionAndProceed(ctx, user, permission);

        };

    }

    public Handler<RoutingContext> requireRole(String requiredRole) {

        return ctx -> {

            JsonObject user = ctx.get("currentUser");

            if (user == null) {

                ApiResponse.sendError(ctx, 401, "UNAUTHORIZED", "Authentication required");

                return;

            }

            String roleName = user.getString("roleName");

            Long roleId = user.getLong("roleId");

            boolean hasRole = requiredRole.equalsIgnoreCase(roleName) || "ROLE_ADMIN".equalsIgnoreCase(roleName) || (roleId != null && roleId == 1L);

            if (hasRole) {

                ctx.next();

            } else {

                ApiResponse.sendError(ctx, 403, "FORBIDDEN", "Access denied: Role " + requiredRole + " required");

            }

        };

    }

    public Handler<RoutingContext> requireAdmin() {

        return requireRole("ROLE_ADMIN");

    }

    public Handler<RoutingContext> authenticateHandler() {

        return this::authenticate;

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
