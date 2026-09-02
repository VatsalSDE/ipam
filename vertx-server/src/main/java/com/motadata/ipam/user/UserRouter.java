package com.motadata.ipam.user;


import com.motadata.ipam.security.RbacAuthHandler;

import io.vertx.core.Vertx;

import io.vertx.ext.web.Router;

import io.vertx.mysqlclient.MySQLPool;

/**
 * UserRouter registers REST endpoints for User and Role Management.
 */
public class UserRouter {

    public static void register(Router router, MySQLPool mysqlPool, Vertx vertx, RbacAuthHandler rbacAuthHandler) {

        UserService userService = new UserService(mysqlPool, vertx);

        UserHandler handler = new UserHandler(userService);

        // --- 1. ROLES & PERMISSIONS MANAGEMENT ---

        // List Roles
        router.get("/api/user/roles")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_READ"))
                .handler(handler::listRoles);

        // List All 7 Features (Matrix columns)
        router.get("/api/user/roles/features")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_READ"))
                .handler(handler::listFeatures);

        // Get Role Feature Permissions by Role ID
        router.get("/api/user/roles/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_READ"))
                .handler(handler::getRolePermissions);

        // Create New Role (Admin only)
        router.post("/api/user/roles")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::createRole);

        // Update Role & Permissions (Admin only)
        router.put("/api/user/roles/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::updateRole);

        // Delete Role (Admin only)
        router.delete("/api/user/roles/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::deleteRole);

        // --- 2. USER PROFILE MANAGEMENT ---

        // List Users
        router.get("/api/user")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_READ"))
                .handler(handler::list);

        // Create User (Admin only)
        router.post("/api/user")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::create);

        // Change Password (Self or Admin - IDOR protected)
        router.put("/api/user/:id/password")
                .handler(rbacAuthHandler.authenticateHandler())
                .handler(handler::changePassword);

        // Get Single User (Self or Admin - IDOR protected)
        router.get("/api/user/:id")
                .handler(rbacAuthHandler.authenticateHandler())
                .handler(handler::get);

        // Update User Profile (Self or Admin - privilege escalation protected)
        router.put("/api/user/:id")
                .handler(rbacAuthHandler.authenticateHandler())
                .handler(handler::update);

        // Delete User (Admin only - prevents self/admin deletion)
        router.delete("/api/user/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::delete);

    }

}
