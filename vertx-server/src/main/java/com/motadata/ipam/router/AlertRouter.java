package com.motadata.ipam.router;

import com.motadata.ipam.api.AlertHandler;

import com.motadata.ipam.security.RbacAuthHandler;

import com.motadata.ipam.service.AlertService;

import io.vertx.core.Vertx;

import io.vertx.ext.web.Router;

import io.vertx.mysqlclient.MySQLPool;

/**
 * AlertRouter registers endpoints for alert streams and notifications.
 */
public class  AlertRouter {

    public static void register(Router router, MySQLPool mysqlPool, Vertx vertx, RbacAuthHandler rbacAuthHandler) {

        AlertService alertService = new AlertService(mysqlPool, vertx);

        AlertHandler handler = new AlertHandler(alertService);

        router.get("/api/alerts")
                .handler(rbacAuthHandler.requirePermission("PERM_ALERTS_READ"))
                .handler(handler::list);

        router.post("/api/alerts")
                .handler(rbacAuthHandler.requirePermission("PERM_ALERTS_WRITE"))
                .handler(handler::create);

        router.put("/api/alerts/:id/clear")
                .handler(rbacAuthHandler.requirePermission("PERM_ALERTS_WRITE"))
                .handler(handler::clear);

        router.delete("/api/alerts/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_ALERTS_WRITE"))
                .handler(handler::delete);

    }

}
