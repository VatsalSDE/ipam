package com.motadata.ipam.core;


import com.motadata.ipam.alert.AlertRouter;
import com.motadata.ipam.alert.AlertService;
import com.motadata.ipam.auth.AuthRouter;
import com.motadata.ipam.core.health.HealthRouter;
import com.motadata.ipam.core.middleware.CorrelationIdHandler;
import com.motadata.ipam.core.model.ApiResponse;
import com.motadata.ipam.dashboard.DashboardRouter;
import com.motadata.ipam.dashboard.DashboardService;
import com.motadata.ipam.event.EventRouter;
import com.motadata.ipam.event.EventService;
import com.motadata.ipam.gateway.GatewayRouter;
import com.motadata.ipam.gateway.GatewayService;
import com.motadata.ipam.maintenance.DatabaseMaintenanceRouter;
import com.motadata.ipam.request.IpRequestRouter;
import com.motadata.ipam.rogue.RogueDetectionRouter;
import com.motadata.ipam.rogue.RogueDetectionService;
import com.motadata.ipam.scanner.GoPluginBridge;
import com.motadata.ipam.security.JwtTokenService;
import com.motadata.ipam.security.RbacAuthHandler;
import com.motadata.ipam.security.SecurityUtil;
import com.motadata.ipam.subnet.SubnetRouter;
import com.motadata.ipam.subnet.SubnetService;
import com.motadata.ipam.user.UserRouter;

import io.vertx.core.Vertx;

import io.vertx.ext.web.Router;

import io.vertx.ext.web.handler.BodyHandler;

import io.vertx.ext.web.handler.LoggerHandler;

import io.vertx.ext.web.handler.TimeoutHandler;

import io.vertx.ext.web.handler.StaticHandler;

import io.vertx.sqlclient.Pool;

/**
 * AppRouter is the central router compositor that registers global middleware,
 * error handling, and mounts domain sub-routers.
 */
public class AppRouter {

    public static Router create(Vertx vertx, Pool mysqlPool, GoPluginBridge goPluginBridge, JwtTokenService jwtTokenService, RbacAuthHandler rbacAuthHandler) {

        Router router = Router.router(vertx);

//        STAGE 1: Global Enterprise Middleware Pipeline
//
//     1. CorrelationIdHandler ──► Injects X-Correlation-ID header & SLF4J MDC
//     2. SecurityUtil         ──► Applies nosniff, DENY frame, HSTS, XSS headers
//     3. LoggerHandler        ──► Logs incoming requests (Method, URI, Status)
//     4. TimeoutHandler       ──► Enforces 30-second SLA timeout on hanging requests
//     5. BodyHandler          ──► Parses JSON request bodies into memory buffer
        router.route().handler(CorrelationIdHandler.create());

        router.route().handler(SecurityUtil::applySecurityHeaders);    // Java Method Reference (::): SecurityUtil.java:public static void applySecurityHeaders(RoutingContext ctx)

        router.route().handler(LoggerHandler.create());

        TimeoutHandler timeoutHandler = TimeoutHandler.create(30000);

        router.route().handler(ctx -> {

            if (ctx.normalizedPath().startsWith("/api/alerts/stream")) {

                ctx.next();

            } else {

                timeoutHandler.handle(ctx);

            }

        });

        router.route().handler(BodyHandler.create());

//        STAGE 2: Global Exception & Saturation Interceptor
//
//     router.route().failureHandler(ApiResponse::handleFailure)
//     ├── Catches uncaught runtime exceptions (Returns clean 500 JSON)
//     └── Catches PoolQueueFullException (Returns 503 + Retry-After backpressure)
        // global failure handler if suppose nullpointer or else exception occurs and all
        // Catches anything that slipped through without being handled.
        router.route().failureHandler(ApiResponse::handleFailure);

//        STAGE 3: Modular Domain Sub-Routers (RBAC & Business Endpoints)
//
//     ├── HealthRouter   ──► /health, /api/health
//     ├── AuthRouter     ──► /api/auth/login, /refresh, /logout, /me
//     ├── SubnetRouter   ──► /api/subnet, /ips, /check, /scan
//     └── GatewayRouter  ──► /api/gateway, /discovered-subnet

//        Order Matters:
//      • If BodyHandler didn't run first, when your controller calls ctx.body().asJsonObject(), the body would be empty/null because the network stream hasn't been read into memory yet!
//      • Running BodyHandler upfront ensures that every subsequent controller automatically receives a parsed JSON body.

        HealthRouter.register(router, mysqlPool);

        AuthRouter.register(router, mysqlPool, jwtTokenService, rbacAuthHandler);

        SubnetRouter.register(router, mysqlPool, goPluginBridge, vertx, rbacAuthHandler);

        GatewayRouter.register(router, mysqlPool, goPluginBridge, vertx, rbacAuthHandler);

        EventRouter.register(router, mysqlPool, vertx, rbacAuthHandler);

        AlertRouter.register(router, mysqlPool, vertx, rbacAuthHandler);

        RogueDetectionRouter.register(router, mysqlPool, vertx, rbacAuthHandler);

        UserRouter.register(router, mysqlPool, vertx, rbacAuthHandler);

        DatabaseMaintenanceRouter.register(router, mysqlPool, vertx, rbacAuthHandler);

        IpRequestRouter.register(router, mysqlPool, vertx, rbacAuthHandler);

        DashboardService dashboardService = new DashboardService(
                new SubnetService(mysqlPool, vertx),
                new GatewayService(mysqlPool, vertx),
                new EventService(mysqlPool, vertx),
                new RogueDetectionService(mysqlPool, vertx),
                new AlertService(mysqlPool, vertx),
                mysqlPool
        );
        DashboardRouter.register(router, dashboardService, rbacAuthHandler);

        // 4. Mount Frontend Static Web Assets
        router.route("/*").handler(StaticHandler.create("webroot").setCachingEnabled(false).setIndexPage("index.html"));

        return router;

    }

}
