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

        // 1. Get Global Database Maintenance & Retention Settings
        router.get("/api/database-maintenance")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_READ"))
                .handler(handler::getSettings);

        // 2. Update Global Database Maintenance & Retention Settings
        router.put("/api/database-maintenance")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::updateSettings);

        // 3. Execute Retention Purge / Archive
        router.post("/api/database-maintenance/purge")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::purge);

        // 4. Execute Physical Database Export / Backup
        router.post("/api/database-maintenance/backup")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::runBackup);

    }

}
