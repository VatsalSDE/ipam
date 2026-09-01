package com.motadata.ipam.router;

import com.motadata.ipam.model.ApiResponse;

import com.motadata.ipam.plugin.GoPluginBridge;

import com.motadata.ipam.security.JwtTokenService;

import com.motadata.ipam.security.RbacAuthHandler;

import com.motadata.ipam.security.SecurityUtil;

import io.vertx.core.Vertx;

import io.vertx.ext.web.Router;

import io.vertx.ext.web.handler.BodyHandler;

import io.vertx.ext.web.handler.LoggerHandler;

import io.vertx.ext.web.handler.StaticHandler;

import io.vertx.mysqlclient.MySQLPool;

/**
 * AppRouter is the central router compositor that registers global middleware,
 * error handling, and mounts domain sub-routers.
 */
public class AppRouter {

    public static Router create(Vertx vertx, MySQLPool mysqlPool, GoPluginBridge goPluginBridge, JwtTokenService jwtTokenService, RbacAuthHandler rbacAuthHandler) {

        Router router = Router.router(vertx);

//        STAGE 1: Global Enterprise Middleware Pipeline
//
//     1. CorrelationIdHandler ──► Injects X-Correlation-ID header & SLF4J MDC
//     2. SecurityUtil         ──► Applies nosniff, DENY frame, HSTS, XSS headers
//     3. LoggerHandler        ──► Logs incoming requests (Method, URI, Status)
//     4. BodyHandler          ──► Parses JSON request bodies into memory buffer
        router.route().handler(CorrelationIdHandler.create());

        router.route().handler(SecurityUtil::applySecurityHeaders);

        router.route().handler(LoggerHandler.create());

        router.route().handler(BodyHandler.create());

//        STAGE 2: Global Exception & Saturation Interceptor
//
//     router.route().failureHandler(ApiResponse::handleFailure)
//     ├── Catches uncaught runtime exceptions (Returns clean 500 JSON)
//     └── Catches PoolQueueFullException (Returns 503 + Retry-After backpressure)
        router.route().failureHandler(ApiResponse::handleFailure);

//        STAGE 3: Modular Domain Sub-Routers (RBAC & Business Endpoints)
//
//     ├── HealthRouter   ──► /health, /api/health
//     ├── AuthRouter     ──► /api/auth/login, /refresh, /logout, /me
//     ├── SubnetRouter   ──► /api/subnet, /ips, /check, /scan
//     └── GatewayRouter  ──► /api/gateway, /discovered-subnet

        HealthRouter.register(router, mysqlPool);

        AuthRouter.register(router, mysqlPool, jwtTokenService, rbacAuthHandler);

        SubnetRouter.register(router, mysqlPool, goPluginBridge, vertx, rbacAuthHandler);

        GatewayRouter.register(router, mysqlPool, goPluginBridge, vertx, rbacAuthHandler);

        EventRouter.register(router, mysqlPool, vertx, rbacAuthHandler);

        AlertRouter.register(router, mysqlPool, vertx, rbacAuthHandler);

        RogueDetectionRouter.register(router, mysqlPool, vertx, rbacAuthHandler);

        UserRouter.register(router, mysqlPool, vertx, rbacAuthHandler);

        DatabaseMaintenanceRouter.register(router, mysqlPool, vertx, rbacAuthHandler);

        // 4. Mount Frontend Static Web Assets
        router.route("/*").handler(StaticHandler.create("webroot").setCachingEnabled(false).setIndexPage("index.html"));

        return router;

    }

}
