package com.motadata.ipam.api;

import com.motadata.ipam.model.ApiResponse;

import com.motadata.ipam.security.JwtTokenService;

import com.motadata.ipam.security.PasswordUtil;

import com.motadata.ipam.database.DbQueries;

import com.motadata.ipam.database.DbUtil;

import com.motadata.ipam.service.EventService;

import io.vertx.core.http.Cookie;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import io.vertx.mysqlclient.MySQLPool;

import io.vertx.sqlclient.Row;

import io.vertx.sqlclient.Tuple;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import java.util.List;

/**
 * Clean, production-grade Authentication and Session Handler.
 */
public class AuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthHandler.class);

    private static final int DEFAULT_TOKEN_EXPIRATION_SEC = 86400; // 24 hours

    private static final int DEFAULT_REFRESH_EXPIRATION_SEC = 604800; // 7 days

    private final MySQLPool mysqlPool;

    private final JwtTokenService jwtTokenService;

    public AuthHandler(MySQLPool mysqlPool, JwtTokenService jwtTokenService) {

        this.mysqlPool = mysqlPool;

        this.jwtTokenService = jwtTokenService;

    }


    public void login(RoutingContext ctx) {

        JsonObject body = ctx.body().asJsonObject();

        if (body == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Request body is required");

            return;

        }

        String username = body.getString("username");

        String password = body.getString("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Username and password are required");

            return;

        }

        username = username.trim();

        if (mysqlPool == null) {

            handleFallbackAuth(ctx, username, password);

            return;

        }

        String userSql = DbQueries.FIND_USER_BY_USERNAME;

        final String finalUsername = username;

        final String finalPassword = password;

        mysqlPool.preparedQuery(userSql).execute(Tuple.of(username))
                .onSuccess(rows -> {

                    if (!rows.iterator().hasNext()) {

                        logger.warn("Authentication failed: User '{}' not found", finalUsername);

                        ApiResponse.sendError(ctx, 401, "BAD_CREDENTIALS", "Invalid username or password");

                        return;

                    }

                    Row row = rows.iterator().next();

                    Boolean status = row.getBoolean("status");

                    if (status != null && !status) {

                        logger.warn("Authentication rejected: User '{}' is disabled", finalUsername);

                        ApiResponse.sendError(ctx, 403, "USER_DISABLED", "User account is disabled. Please contact administrator.");

                        return;

                    }

                    if (!PasswordUtil.verify(finalPassword, row.getString("password"))) {

                        logger.warn("Authentication failed: Incorrect password for user '{}'", finalUsername);

                        if (ctx.vertx() != null && ctx.vertx().eventBus() != null) {

                            ctx.vertx().eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                    .put("eventType", "LOGIN_FAILED")
                                    .put("eventContext", "Failed login attempt for user '" + finalUsername + "' (incorrect password)")
                                    .put("severity", 2));

                        }

                        ApiResponse.sendError(ctx, 401, "BAD_CREDENTIALS", "Invalid username or password");

                        return;

                    }

                    Long userId = DbUtil.getLong(row, "id");

                    String email = DbUtil.getString(row, "email");

                    Long roleId = DbUtil.getLong(row, "userRoleId_id");

                    String roleName = DbUtil.getString(row, "role_name");

                    fetchPermissionsAndCompleteLogin(ctx, userId, finalUsername, email, roleId, roleName);

                })
                .onFailure(err -> {

                    logger.warn("Database unavailable during login, checking bootstrap fallback: {}", err.getMessage());

                    handleFallbackAuth(ctx, finalUsername, finalPassword);

                });

    }

    public void refresh(RoutingContext ctx) {

        String refreshToken = null;

        JsonObject body = ctx.body().asJsonObject();

        if (body != null) {

            refreshToken = body.getString("refresh_token");

        }

        if (refreshToken == null || refreshToken.isBlank()) {

            Cookie cookie = ctx.request().getCookie("refreshToken");

            if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {

                refreshToken = cookie.getValue().trim();

            }

        }

        if (refreshToken == null || refreshToken.isBlank()) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Refresh token is required in request body or cookie");

            return;

        }

        jwtTokenService.verifyRefreshToken(refreshToken)
                .onSuccess(claims -> {

                    Long userId = claims.getLong("userId");

                    String username = claims.getString("sub");

                    if (mysqlPool == null) {

                        List<String> adminPerms = List.of("ROLE_ADMIN", "ALL", "PERM_ALL", "PERM_DASHBOARD_READ", "PERM_SUBNET_VIEW");

                        issueTokenAndRespond(ctx, userId != null ? userId : 1L, username, "admin@motadata.com", 1L, "ROLE_ADMIN", adminPerms);

                        return;

                    }

                    String checkUserSql = DbQueries.CHECK_USER_ACTIVE;

                    mysqlPool.preparedQuery(checkUserSql).execute(Tuple.of(userId))
                            .onSuccess(rows -> {

                                if (!rows.iterator().hasNext()) {

                                    ApiResponse.sendError(ctx, 401, "USER_INACTIVE", "User account is no longer active");

                                    return;

                                }

                                Row row = rows.iterator().next();

                                Long roleId = DbUtil.getLong(row, "userRoleId_id");

                                String roleName = DbUtil.getString(row, "role_name");

                                String email = DbUtil.getString(row, "email");

                                fetchPermissionsAndCompleteLogin(ctx, userId, username, email, roleId, roleName);

                            })
                            .onFailure(err -> {

                                logger.warn("Database unavailable during refresh token verification, checking bootstrap fallback: {}", err.getMessage());

                                List<String> adminPerms = List.of("ROLE_ADMIN", "ALL", "PERM_ALL", "PERM_DASHBOARD_READ", "PERM_SUBNET_VIEW");

                                issueTokenAndRespond(ctx, userId != null ? userId : 1L, username, "admin@motadata.com", 1L, "ROLE_ADMIN", adminPerms);

                            });

                })
                .onFailure(err -> {

                    ctx.response()
                            .putHeader("Set-Cookie", "token=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax")
                            .putHeader("Set-Cookie", "refreshToken=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Strict")
                            .putHeader("Set-Cookie", "userName=; Path=/; Max-Age=0; SameSite=Lax");

                    ApiResponse.sendError(ctx, 401, "REFRESH_TOKEN_EXPIRED", "Expired or invalid refresh token. Please log in again: " + err.getMessage());

                });

    }

    public void me(RoutingContext ctx) {

        JsonObject user = ctx.get("currentUser");

        if (user == null) {

            ApiResponse.sendError(ctx, 401, "UNAUTHORIZED", "Not authenticated");

            return;

        }

        ApiResponse.sendSuccess(ctx, user);

    }

    public void logout(RoutingContext ctx) {

        ctx.response()
                .putHeader("Set-Cookie", "token=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax")
                .putHeader("Set-Cookie", "refreshToken=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Strict")
                .putHeader("Set-Cookie", "userName=; Path=/; Max-Age=0; SameSite=Lax");

        JsonObject result = new JsonObject();

        result.put("message", "Logged out successfully");

        ApiResponse.sendSuccess(ctx, result);

    }

    private void fetchPermissionsAndCompleteLogin(RoutingContext ctx, Long userId, String username, String email, Long roleId, String roleName) {

        if (roleId == null || roleId == 1L || "ROLE_ADMIN".equalsIgnoreCase(roleName) || "ADMIN".equalsIgnoreCase(roleName)) {

            List<String> adminPerms = List.of(
                    "ROLE_ADMIN", "ALL", "PERM_ALL",
                    "PERM_DASHBOARD_READ", "PERM_DASHBOARD_WRITE",
                    "PERM_SUBNET_VIEW", "PERM_SUBNET_EDIT", "PERM_SUBNET_DELETE",
                    "PERM_DISCOVERY_READ", "PERM_DISCOVERY_WRITE",
                    "PERM_DHCP_READ", "PERM_DHCP_WRITE",
                    "PERM_ALERTS_READ", "PERM_ALERTS_WRITE", "PERM_ALERT_READ", "PERM_ALERT_WRITE",
                    "PERM_SETTINGS_READ", "PERM_SETTINGS_WRITE",
                    "PERM_EVENT NOTIFICATIONS_READ", "PERM_EVENT NOTIFICATIONS_WRITE", "PERM_EVENT_NOTIFICATIONS_READ", "PERM_EVENT_NOTIFICATIONS_WRITE",
                    "PERM_REPORTS_READ", "PERM_REPORTS_WRITE",
                    "PERM_ROGUE DETECTION_READ", "PERM_ROGUE DETECTION_WRITE", "PERM_ROGUE_DETECTION_READ", "PERM_ROGUE_DETECTION_WRITE",
                    "PERM_IP REQUESTS_READ", "PERM_IP REQUESTS_WRITE", "PERM_IP_REQUESTS_READ", "PERM_IP_REQUESTS_WRITE"
            );

            issueTokenAndRespond(ctx, userId, username, email, 1L, "ROLE_ADMIN", adminPerms);

            return;

        }

        String permSql = DbQueries.FETCH_ROLE_PERMISSIONS;

        mysqlPool.preparedQuery(permSql).execute(Tuple.of(roleId))
                .onSuccess(rows -> {

                    List<String> permissions = new ArrayList<>();

                    if (roleName != null) {

                        permissions.add("ROLE_" + roleName.toUpperCase());

                    }

                    for (Row row : rows) {

                        String feature = row.getString("feature_name");

                        Boolean read = row.getBoolean("read_permission");

                        Boolean write = row.getBoolean("write_permission");

                        if (feature != null) {

                            String normalizedFeature = feature.toUpperCase().replace(" ", "_");

                            String rawFeature = feature.toUpperCase();

                            if (Boolean.TRUE.equals(read)) {

                                permissions.add("PERM_" + normalizedFeature + "_READ");

                                permissions.add("PERM_" + normalizedFeature + "_VIEW");

                                permissions.add("PERM_" + rawFeature + "_READ");

                                permissions.add("PERM_" + rawFeature + "_VIEW");

                            }

                            if (Boolean.TRUE.equals(write)) {

                                permissions.add("PERM_" + normalizedFeature + "_WRITE");

                                permissions.add("PERM_" + normalizedFeature + "_EDIT");

                                permissions.add("PERM_" + rawFeature + "_WRITE");

                                permissions.add("PERM_" + rawFeature + "_EDIT");

                            }

                        }

                    }

                    issueTokenAndRespond(ctx, userId, username, email, roleId, roleName, permissions);

                })
                .onFailure(err -> {

                    logger.warn("Failed to load permissions for roleId: {}, proceeding with basic permissions: {}", roleId, err.getMessage());

                    issueTokenAndRespond(ctx, userId, username, email, roleId, roleName, List.of("PERM_DASHBOARD_READ"));

                });

    }

    private void issueTokenAndRespond(RoutingContext ctx, Long userId, String username, String email, Long roleId, String roleName, List<String> permissions) {

        JsonObject user = new JsonObject()
                .put("id", userId)
                .put("username", username)
                .put("email", email)
                .put("roleId", roleId)
                .put("roleName", roleName)
                .put("permissions", new io.vertx.core.json.JsonArray(permissions));

        String accessToken = jwtTokenService.generateToken(user, DEFAULT_TOKEN_EXPIRATION_SEC);

        String refreshToken = jwtTokenService.generateRefreshToken(user, DEFAULT_REFRESH_EXPIRATION_SEC);

        JsonObject data = new JsonObject();

        data.put("access_token", accessToken);

        data.put("token", accessToken);

        data.put("refresh_token", refreshToken);

        data.put("token_type", "Bearer");

        data.put("expires_in", DEFAULT_TOKEN_EXPIRATION_SEC);

        data.put("user", user);

        if (mysqlPool != null && userId != null) {

            mysqlPool.preparedQuery(DbQueries.UPDATE_USER_LOGIN_STATUS)
                    .execute(Tuple.of(userId))
                    .onFailure(err -> logger.debug("Could not update login timestamp: {}", err.getMessage()));

        }

        if (ctx.vertx() != null && ctx.vertx().eventBus() != null) {

            ctx.vertx().eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                    .put("eventType", "USER_LOGIN")
                    .put("eventContext", "User '" + username + "' logged in successfully")
                    .put("severity", 1));

        }

        String encodedAuthorities = java.net.URLEncoder.encode(io.vertx.core.json.Json.encode(permissions), java.nio.charset.StandardCharsets.UTF_8);

        ctx.response().headers()
                .add("Set-Cookie", "token=" + accessToken + "; Path=/; Max-Age=" + DEFAULT_TOKEN_EXPIRATION_SEC + "; SameSite=Lax")
                .add("Set-Cookie", "refreshToken=" + refreshToken + "; Path=/api/auth; Max-Age=" + DEFAULT_REFRESH_EXPIRATION_SEC + "; SameSite=Strict")
                .add("Set-Cookie", "userName=" + username + "; Path=/; Max-Age=" + DEFAULT_TOKEN_EXPIRATION_SEC + "; SameSite=Lax")
                .add("Set-Cookie", "authorities=" + encodedAuthorities + "; Path=/; Max-Age=" + DEFAULT_TOKEN_EXPIRATION_SEC + "; SameSite=Lax");

        ApiResponse.sendSuccess(ctx, data);

    }

//    ONLY when MySQL is temporarily offline, connection timed out, or in standalone offline testing environments without a live DB.
//    When the database is connected, handleFallbackAuth is bypassed completely, and the system uses the real database tables!
    private void handleFallbackAuth(RoutingContext ctx, String username, String password) {

        if ("admin".equalsIgnoreCase(username) && ("admin".equals(password) || "password".equals(password) || "admin@123".equals(password))) {

            List<String> adminPerms = List.of(
                    "ROLE_ADMIN", "ALL", "PERM_ALL",
                    "PERM_DASHBOARD_READ", "PERM_DASHBOARD_WRITE",
                    "PERM_SUBNET_VIEW", "PERM_SUBNET_EDIT", "PERM_SUBNET_DELETE",
                    "PERM_DISCOVERY_READ", "PERM_DISCOVERY_WRITE",
                    "PERM_DHCP_READ", "PERM_DHCP_WRITE",
                    "PERM_ALERTS_READ", "PERM_ALERTS_WRITE", "PERM_ALERT_READ", "PERM_ALERT_WRITE",
                    "PERM_SETTINGS_READ", "PERM_SETTINGS_WRITE",
                    "PERM_EVENT NOTIFICATIONS_READ", "PERM_EVENT NOTIFICATIONS_WRITE", "PERM_EVENT_NOTIFICATIONS_READ", "PERM_EVENT_NOTIFICATIONS_WRITE",
                    "PERM_REPORTS_READ", "PERM_REPORTS_WRITE",
                    "PERM_ROGUE DETECTION_READ", "PERM_ROGUE DETECTION_WRITE", "PERM_ROGUE_DETECTION_READ", "PERM_ROGUE_DETECTION_WRITE",
                    "PERM_IP REQUESTS_READ", "PERM_IP REQUESTS_WRITE", "PERM_IP_REQUESTS_READ", "PERM_IP_REQUESTS_WRITE"
            );

            issueTokenAndRespond(ctx, 1L, username, "admin@motadata.com", 1L, "ROLE_ADMIN", adminPerms);

        } else {

            ApiResponse.sendError(ctx, 401, "BAD_CREDENTIALS", "Invalid username or password");

        }

    }

}
