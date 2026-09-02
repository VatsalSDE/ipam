package com.motadata.ipam.event;


import com.motadata.ipam.security.RbacAuthHandler;

import io.vertx.core.Vertx;

import io.vertx.ext.web.Router;

import io.vertx.mysqlclient.MySQLPool;

/**
 * EventRouter registers endpoints for event notifications and audit logging.
 */
public class EventRouter {

    public static void register(Router router, MySQLPool mysqlPool, Vertx vertx, RbacAuthHandler rbacAuthHandler) {

        EventService eventService = new EventService(mysqlPool, vertx);

        EventHandler handler = new EventHandler(eventService);

        router.get("/api/event")
                .handler(rbacAuthHandler.requirePermission("PERM_EVENT NOTIFICATIONS_READ"))
                .handler(handler::list);

        router.get("/api/event/top")
                .handler(rbacAuthHandler.requirePermission("PERM_EVENT NOTIFICATIONS_READ"))
                .handler(handler::listTop);

    }

}
