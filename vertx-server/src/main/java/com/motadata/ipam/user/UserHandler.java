package com.motadata.ipam.user;


import com.motadata.ipam.core.model.ApiResponse;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

/**
 * REST API Handler for User and Role Management.
 */
public class UserHandler {

    private static final Logger logger = LoggerFactory.getLogger(UserHandler.class);

    private final UserService userService;

    public UserHandler(UserService userService) {

        this.userService = userService;

    }

    /**
     * GET /api/user
     * Lists all users in the system.
     */
    public void list(RoutingContext ctx) {

        userService.listUsers()
                .onSuccess(users -> ApiResponse.sendSuccess(ctx, users))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "USER_LIST_FAILED", err.getMessage()));

    }

    /**
     * GET /api/user/:id
     * Gets a single user by ID with BOLA/IDOR protection.
     */
    public void get(RoutingContext ctx) {

        Long userId = parseId(ctx.pathParam("id"));

        if (userId == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Invalid user ID");

            return;

        }

        JsonObject currentUser = ctx.get("currentUser");

        if (currentUser != null && !isSelfOrAdmin(currentUser, userId)) {

            ApiResponse.sendError(ctx, 403, "FORBIDDEN", "BOLA/IDOR Violation: You cannot view another user's account details");

            return;

        }

        userService.getUserById(userId)
                .onSuccess(user -> ApiResponse.sendSuccess(ctx, user))
                .onFailure(err -> ApiResponse.sendError(ctx, 404, "USER_NOT_FOUND", err.getMessage()));

    }

    /**
     * POST /api/user
     * Creates a new user account.
     */
    public void create(RoutingContext ctx) {

        JsonObject body = ctx.body().asJsonObject();

        if (body == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Request body is required");

            return;

        }

        userService.createUser(body)
                .onSuccess(created -> ApiResponse.sendSuccess(ctx, 201, created))
                .onFailure(err -> ApiResponse.sendError(ctx, 400, "USER_CREATE_FAILED", err.getMessage()));

    }

    /**
     * PUT /api/user/:id
     * Updates an existing user account with BOLA/IDOR protection.
     */
    public void update(RoutingContext ctx) {

        Long userId = parseId(ctx.pathParam("id"));

        if (userId == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Invalid user ID");

            return;

        }

        JsonObject currentUser = ctx.get("currentUser");

        if (currentUser != null && !isSelfOrAdmin(currentUser, userId)) {

            ApiResponse.sendError(ctx, 403, "FORBIDDEN", "BOLA/IDOR Violation: You cannot modify another user's profile");

            return;

        }

        JsonObject body = ctx.body().asJsonObject();

        if (body == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Request body is required");

            return;

        }

        // Non-admin users cannot escalate privileges (change their own role or status)
        if (currentUser != null && !isAdmin(currentUser)) {

            body.remove("userRoleId");

            body.remove("roleId");

            body.remove("status");

        }

        userService.updateUser(userId, body)
                .onSuccess(updated -> ApiResponse.sendSuccess(ctx, updated))
                .onFailure(err -> ApiResponse.sendError(ctx, 400, "USER_UPDATE_FAILED", err.getMessage()));

    }

    /**
     * DELETE /api/user/:id
     * Removes a user account (Admin only).
     */
    public void delete(RoutingContext ctx) {

        Long userId = parseId(ctx.pathParam("id"));

        if (userId == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Invalid user ID");

            return;

        }

        JsonObject currentUser = ctx.get("currentUser");

        if (currentUser != null && !isAdmin(currentUser)) {

            ApiResponse.sendError(ctx, 403, "FORBIDDEN", "Access denied: Administrator privileges required to delete user accounts");

            return;

        }

        Long currentUserId = currentUser != null ? currentUser.getLong("userId", currentUser.getLong("id")) : null;

        userService.deleteUser(userId, currentUserId)
                .onSuccess(res -> ApiResponse.sendSuccess(ctx, res))
                .onFailure(err -> ApiResponse.sendError(ctx, 400, "USER_DELETE_FAILED", err.getMessage()));

    }

    /**
     * PUT /api/user/:id/password or PUT /api/changePassword/:id
     * Changes user password with BOLA/IDOR protection.
     */
    public void changePassword(RoutingContext ctx) {

        Long userId = parseId(ctx.pathParam("id"));

        if (userId == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Invalid user ID");

            return;

        }

        JsonObject currentUser = ctx.get("currentUser");

        if (currentUser != null && !isSelfOrAdmin(currentUser, userId)) {

            ApiResponse.sendError(ctx, 403, "FORBIDDEN", "BOLA/IDOR Violation: You cannot change another user's password");

            return;

        }

        JsonObject body = ctx.body().asJsonObject();

        if (body == null || body.getString("password") == null || body.getString("password").isBlank()) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "New password is required");

            return;

        }

        userService.changePassword(userId, body.getString("password"))
                .onSuccess(v -> ApiResponse.sendSuccess(ctx, new JsonObject().put("message", "Password changed successfully")))
                .onFailure(err -> ApiResponse.sendError(ctx, 400, "PASSWORD_CHANGE_FAILED", err.getMessage()));

    }

    /**
     * GET /api/user/roles or GET /api/userRole
     * Lists all user roles.
     */
    public void listRoles(RoutingContext ctx) {

        userService.listRoles()
                .onSuccess(roles -> ApiResponse.sendSuccess(ctx, roles))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "ROLES_LIST_FAILED", err.getMessage()));

    }

    /**
     * GET /api/userRole/feature/ or GET /api/user/roles/features
     * Lists system features for permission checkboxes.
     */
    public void listFeatures(RoutingContext ctx) {

        userService.listFeatures()
                .onSuccess(features -> ApiResponse.sendSuccess(ctx, features))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "FEATURES_LIST_FAILED", err.getMessage()));

    }

    /**
     * Direct array response for KendoGrid transport: GET /userRole/feature/
     */
    public void listFeaturesDirect(RoutingContext ctx) {

        userService.listFeatures()
                .onSuccess(features -> ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(features.encode()))
                .onFailure(err -> ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("error", err.getMessage()).encode()));

    }

    /**
     * GET /api/user/roles/:id
     * Gets full role object with its permissions.
     */
    public void getRole(RoutingContext ctx) {

        Long roleId = parseId(ctx.pathParam("id"));

        if (roleId == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Invalid role ID");

            return;

        }

        userService.getRoleById(roleId)
                .onSuccess(role -> ApiResponse.sendSuccess(ctx, role))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "ROLE_GET_FAILED", err.getMessage()));

    }

    public void getRolePermissions(RoutingContext ctx) {

        getRole(ctx);

    }

    /**
     * POST /api/userRole
     * Creates a new user role.
     */
    public void createRole(RoutingContext ctx) {

        JsonObject body = ctx.body().asJsonObject();

        if (body == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Request body is required");

            return;

        }

        userService.createRole(body)
                .onSuccess(created -> ApiResponse.sendSuccess(ctx, 201, created))
                .onFailure(err -> ApiResponse.sendError(ctx, 400, "ROLE_CREATE_FAILED", err.getMessage()));

    }

    /**
     * PUT /api/userRole or PUT /api/userRole/:id
     * Updates an existing user role.
     */
    public void updateRole(RoutingContext ctx) {

        JsonObject body = ctx.body().asJsonObject();

        if (body == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Request body is required");

            return;

        }

        Long roleId = parseId(ctx.pathParam("id"));

        if (roleId == null && body.containsKey("id")) {

            roleId = body.getLong("id");

        }

        if (roleId == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Role ID is required");

            return;

        }

        userService.updateRole(roleId, body)
                .onSuccess(updated -> ApiResponse.sendSuccess(ctx, updated))
                .onFailure(err -> ApiResponse.sendError(ctx, 400, "ROLE_UPDATE_FAILED", err.getMessage()));

    }

    /**
     * DELETE /api/userRole/:id
     * Deletes a user role.
     */
    public void deleteRole(RoutingContext ctx) {

        Long roleId = parseId(ctx.pathParam("id"));

        if (roleId == null) {

            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Invalid role ID");

            return;

        }

        userService.deleteRole(roleId)
                .onSuccess(res -> ApiResponse.sendSuccess(ctx, res))
                .onFailure(err -> ApiResponse.sendError(ctx, 400, "ROLE_DELETE_FAILED", err.getMessage()));

    }

    private Long parseId(String param) {

        if (param == null || param.isBlank()) {

            return null;

        }

        try {

            return Long.parseLong(param.trim());

        } catch (NumberFormatException e) {

            return null;

        }

    }

    private boolean isAdmin(JsonObject currentUser) {

        if (currentUser == null) {

            return false;

        }

        Long roleId = currentUser.getLong("roleId");

        String roleName = currentUser.getString("roleName");

        return (roleId != null && roleId == 1L) || "ROLE_ADMIN".equalsIgnoreCase(roleName);

    }

    private boolean isSelfOrAdmin(JsonObject currentUser, Long targetUserId) {

        if (currentUser == null || targetUserId == null) {

            return false;

        }

        if (isAdmin(currentUser)) {

            return true;

        }

        Long currentUserId = currentUser.getLong("userId", currentUser.getLong("id"));

        return targetUserId.equals(currentUserId);

    }

}
