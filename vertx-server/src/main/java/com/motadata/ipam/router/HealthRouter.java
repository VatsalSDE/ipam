package com.motadata.ipam.router;

import com.motadata.ipam.api.HealthHandler;

import io.vertx.ext.web.Router;

import io.vertx.mysqlclient.MySQLPool;

/**
 * HealthRouter registers health check and system diagnostic endpoints.
 */
public class HealthRouter {

    public static void register(Router router, MySQLPool mysqlPool) {

        HealthHandler healthHandler = new HealthHandler(mysqlPool);

        router.get("/health").handler(healthHandler::checkHealth);

        router.get("/api/health").handler(healthHandler::checkHealth);

    }

}
