package com.motadata.ipam.alert;


import com.motadata.ipam.security.RbacAuthHandler;

import io.vertx.core.Vertx;

import io.vertx.ext.web.Router;

import io.vertx.sqlclient.Pool;

/**
 * AlertRouter registers endpoints for alert streams and notifications.
 */
public class AlertRouter {

    public static void register(Router router, Pool mysqlPool, Vertx vertx, RbacAuthHandler rbacAuthHandler) {

        AlertService alertService = new AlertService(mysqlPool, vertx);

        AlertHandler handler = new AlertHandler(alertService, vertx);

        router.get("/api/alerts/stream")
                .handler(handler::streamAlerts);

        router.get("/api/alerts")
                .handler(rbacAuthHandler.requirePermission("PERM_ALERTS_READ"))
                .handler(handler::list);

        router.put("/api/alerts/:id/clear")
                .handler(rbacAuthHandler.requirePermission("PERM_ALERTS_WRITE"))
                .handler(handler::clear);

        router.get("/api/alerts/config")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_READ"))
                .handler(handler::getConfig);

        router.put("/api/alerts/config")
                .handler(rbacAuthHandler.requirePermission("PERM_SETTINGS_WRITE"))
                .handler(handler::updateConfig);

        router.delete("/api/alerts/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_ALERTS_WRITE"))
                .handler(handler::delete);

    }

}
