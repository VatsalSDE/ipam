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

        // 1. Global Enterprise Observability & Diagnostics Middleware
        router.route().handler(CorrelationIdHandler.create());

        router.route().handler(SecurityUtil::applySecurityHeaders);

        router.route().handler(LoggerHandler.create());

        router.route().handler(BodyHandler.create());

        // 2. Global Exception & Failure Interceptor
        router.route().failureHandler(ApiResponse::handleFailure);

        // 3. Mount Sub-Routers
        HealthRouter.register(router, mysqlPool);

        AuthRouter.register(router, mysqlPool, jwtTokenService, rbacAuthHandler);

        SubnetRouter.register(router, mysqlPool, goPluginBridge, vertx, rbacAuthHandler);

        // 4. Mount Frontend Static Web Assets
        router.route("/*").handler(StaticHandler.create("webroot").setCachingEnabled(false).setIndexPage("index.html"));

        return router;

    }

}
