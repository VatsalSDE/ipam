package com.motadata.ipam.auth;

import com.motadata.ipam.security.JwtTokenService;

import com.motadata.ipam.security.RbacAuthHandler;

import io.vertx.ext.web.Router;

import io.vertx.sqlclient.Pool;

/**
 * AuthRouter registers all authentication and session endpoints.
 */
public class AuthRouter {

    public static void register(Router router, Pool mysqlPool, JwtTokenService jwtTokenService, RbacAuthHandler rbacAuthHandler) {

        AuthHandler authHandler = new AuthHandler(mysqlPool, jwtTokenService);

        // Standard REST Auth Endpoints
        router.post("/api/auth/login").handler(authHandler::login);

        router.post("/api/auth/refresh").handler(authHandler::refresh);

        router.post("/api/auth/logout").handler(authHandler::logout);

        router.get("/api/auth/me").handler(rbacAuthHandler::authenticate).handler(authHandler::me);

    }

}
