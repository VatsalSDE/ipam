package com.motadata.ipam.maintenance;


import com.motadata.ipam.security.RbacAuthHandler;

import io.vertx.core.Vertx;

import io.vertx.ext.web.Router;

import io.vertx.mysqlclient.MySQLPool;

/**
 * DatabaseMaintenanceRouter registers REST endpoints for Database Maintenance and Retention.
 */
public class DatabaseMaintenanceRouter {

    public static void register(Router router, MySQLPool mysqlPool, Vertx vertx, RbacAuthHandler rbacAuthHandler) {

        DatabaseMaintenanceService service = new DatabaseMaintenanceService(mysqlPool, vertx);

        DatabaseMaintenanceHandler handler = new DatabaseMaintenanceHandler(service);

        // Get Settings
        router.get("/api/database-maintenance")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_READ"))
                .handler(handler::getSettings);

        router.get("/api/databaseMaintenance/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_READ"))
                .handler(handler::getSettings);

        // Update Settings
        router.put("/api/database-maintenance")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::updateSettings);

        router.put("/api/databaseMaintenance/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::updateSettings);

        // Execute Purge / Archive
        router.delete("/api/database-maintenance")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::purge);

        router.delete("/api/databaseMaintenance/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::purge);

        router.post("/api/database-maintenance/purge")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::purge);

    }

}
