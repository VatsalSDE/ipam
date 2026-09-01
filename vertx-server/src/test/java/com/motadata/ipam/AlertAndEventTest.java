package com.motadata.ipam;

import com.motadata.ipam.config.AppConfig;

import com.motadata.ipam.security.JwtTokenService;

import io.vertx.core.CompositeFuture;

import io.vertx.core.Future;

import io.vertx.core.Vertx;

import io.vertx.core.http.HttpClient;

import io.vertx.core.http.HttpMethod;

import io.vertx.core.json.JsonArray;

import io.vertx.core.json.JsonObject;

import io.vertx.junit5.VertxExtension;

import io.vertx.junit5.VertxTestContext;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for EventRouter, AlertRouter, and RogueDetectionRouter.
 */
@ExtendWith(VertxExtension.class)
class AlertAndEventTest {

    private static final int TEST_PORT = 8892;

    private String validAdminToken;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {

        AppConfig.reset();

        com.motadata.ipam.database.DatabasePool.close();

        AppConfig.getInstance().setServerPort(TEST_PORT);

        JwtTokenService tokenService = new JwtTokenService(vertx);

        JsonObject adminUser = new JsonObject()
                .put("id", 1L)
                .put("username", "admin")
                .put("email", "admin@motadata.com")
                .put("roleName", "ROLE_ADMIN")
                .put("permissions", new JsonArray(List.of(
                        "ROLE_ADMIN", "ALL", "PERM_ALL",
                        "PERM_ALERTS_READ", "PERM_ALERTS_WRITE",
                        "PERM_EVENT NOTIFICATIONS_READ", "PERM_EVENT NOTIFICATIONS_WRITE",
                        "PERM_ROGUE DETECTION_READ", "PERM_ROGUE DETECTION_WRITE",
                        "PERM_IP REQUESTS_READ", "PERM_IP REQUESTS_WRITE"
                )));

        validAdminToken = tokenService.generateToken(adminUser, 3600);

        vertx.deployVerticle(new MainVerticle(), testContext.succeedingThenComplete());

    }

    @Test
    void testAlertAndEventSubsystems(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        // 1. Unauthenticated request -> 401 Unauthorized
        client.request(HttpMethod.GET, TEST_PORT, "localhost", "/api/event")
                .compose(req -> req.send())
                .compose(resp -> {

                    assertEquals(401, resp.statusCode(), "Expected 401 for unauthenticated request");

                    List<String> endpoints = List.of(
                            "/api/event",
                            "/api/alerts",
                            "/api/rogue-detection",
                            "/api/database-maintenance"
                    );

                    List<Future> checkFutures = new ArrayList<>();

                    for (String ep : endpoints) {

                        Future<Void> fut = client.request(HttpMethod.GET, TEST_PORT, "localhost", ep)
                                .compose(req -> {

                                    req.putHeader("Authorization", "Bearer " + validAdminToken);

                                    return req.send();

                                })
                                .compose(authResp -> {

                                    return authResp.body().map(body -> {

                                        assertEquals(200, authResp.statusCode(), "Endpoint failed: " + ep + " with body: " + body.toString());

                                        return body;

                                    });

                                })
                                .map(body -> {

                                    JsonObject json = new JsonObject(body);

                                    assertTrue(json.getBoolean("success", false), "Success flag false on " + ep);

                                    assertNotNull(json.getValue("data"), "Data null on " + ep);

                                    return null;

                                });

                        checkFutures.add(fut);

                    }

                    return CompositeFuture.all(checkFutures);

                })
                .onComplete(testContext.succeeding(res -> testContext.completeNow()));

    }

}
