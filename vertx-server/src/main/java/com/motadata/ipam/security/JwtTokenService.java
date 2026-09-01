package com.motadata.ipam.security;

import com.motadata.ipam.config.AppConfig;

import io.vertx.core.Future;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonArray;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.auth.JWTOptions;

import io.vertx.ext.auth.PubSecKeyOptions;

import io.vertx.ext.auth.authentication.TokenCredentials;

import io.vertx.ext.auth.jwt.JWTAuth;

import io.vertx.ext.auth.jwt.JWTAuthOptions;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import java.util.List;

/**
 * JwtTokenService manages issuance and verification of signed JWT Bearer tokens.
 */
public class JwtTokenService {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenService.class);

    private final JWTAuth jwtAuth;

    private static final java.util.concurrent.atomic.AtomicBoolean INITIALIZED_LOGGED = new java.util.concurrent.atomic.AtomicBoolean(false);

    public JwtTokenService(Vertx vertx) {

        AppConfig config = AppConfig.getInstance();

        JWTAuthOptions authOptions = new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setBuffer(config.getJwtSecret()));

        this.jwtAuth = JWTAuth.create(vertx, authOptions);

        if (INITIALIZED_LOGGED.compareAndSet(false, true)) {

            logger.info("Initialized JWTAuth provider with HS256 algorithm");

        }

    }

    // this is for making the access token here liekwise !!!
    public String generateToken(JsonObject user, int expirationSeconds) {

        JsonObject claims = new JsonObject();

        claims.put("sub", user.getString("username"));

        claims.put("username", user.getString("username"));

        claims.put("id", user.getLong("id", user.getLong("userId")));

        claims.put("userId", user.getLong("id", user.getLong("userId")));

        claims.put("email", user.getString("email"));

        claims.put("roleId", user.getLong("roleId"));

        claims.put("roleName", user.getString("roleName"));

        JsonArray perms = user.getJsonArray("permissions");

        claims.put("permissions", perms != null ? perms : new JsonArray());

        JWTOptions options = new JWTOptions()
                .setAlgorithm("HS256")
                .setExpiresInSeconds(expirationSeconds)
                .setIssuer("Motadata-IPAM");

        return jwtAuth.generateToken(claims, options);

    }

    public String generateRefreshToken(JsonObject user, int expirationSeconds) {

        JsonObject claims = new JsonObject();

        claims.put("sub", user.getString("username"));

        claims.put("userId", user.getLong("id", user.getLong("userId")));

        claims.put("tokenType", "refresh");

        JWTOptions options = new JWTOptions()
                .setAlgorithm("HS256")
                .setExpiresInSeconds(expirationSeconds)
                .setIssuer("Motadata-IPAM");

        return jwtAuth.generateToken(claims, options);

    }

    public Future<JsonObject> verifyToken(String token) {

        if (token == null || token.trim().isEmpty()) {

            return Future.failedFuture("Token is missing or empty");

        }

        return jwtAuth.authenticate(new TokenCredentials(token))
                .map(userPrincipal -> userPrincipal.principal());

    }

    public Future<JsonObject> verifyRefreshToken(String token) {

        if (token == null || token.trim().isEmpty()) {

            return Future.failedFuture("Refresh token is missing or empty");

        }

        return verifyToken(token)
                .compose(claims -> {

                    String tokenType = claims.getString("tokenType");

                    if (!"refresh".equalsIgnoreCase(tokenType)) {

                        return Future.failedFuture("Invalid token type: Expected refresh token");

                    }

                    return Future.succeededFuture(claims);

                });

    }

    public JWTAuth getJwtAuth() {

        return jwtAuth;

    }

}
