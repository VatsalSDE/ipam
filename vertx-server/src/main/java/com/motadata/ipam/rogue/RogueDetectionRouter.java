package com.motadata.ipam.rogue;


import com.motadata.ipam.security.RbacAuthHandler;

import io.vertx.core.Vertx;

import io.vertx.ext.web.Router;

import io.vertx.mysqlclient.MySQLPool;

/**
 * RogueDetectionRouter registers endpoints for rogue device detection & whitelisting.
 */
public class RogueDetectionRouter {

    public static void register(Router router, MySQLPool mysqlPool, Vertx vertx, RbacAuthHandler rbacAuthHandler) {

        RogueDetectionService rogueDetectionService = new RogueDetectionService(mysqlPool, vertx);

        RogueDetectionHandler handler = new RogueDetectionHandler(rogueDetectionService);

        router.get("/api/rogue-detection")
                .handler(rbacAuthHandler.requirePermission("PERM_ROGUE DETECTION_READ"))
                .handler(handler::list);

        router.post("/api/rogue-detection")
                .handler(rbacAuthHandler.requirePermission("PERM_ROGUE DETECTION_WRITE"))
                .handler(handler::create);

        router.put("/api/rogue-detection/:id/authenticity")
                .handler(rbacAuthHandler.requirePermission("PERM_ROGUE DETECTION_WRITE"))
                .handler(handler::updateAuthenticity);

        router.delete("/api/rogue-detection/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_ROGUE DETECTION_WRITE"))
                .handler(handler::delete);

    }

}
