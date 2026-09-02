package com.motadata.ipam;


import com.motadata.ipam.MainVerticle;
import com.motadata.ipam.core.config.AppConfig;
import com.motadata.ipam.core.database.DatabasePool;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class UserAndMaintenanceTest {

    private static final int TEST_PORT = 8894;

    private String validAdminToken;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {

        DatabasePool.close();

        AppConfig.reset();

        AppConfig.getInstance().setServerPort(TEST_PORT);

        JwtTokenService tokenService = new JwtTokenService(vertx);

        JsonObject adminUser = new JsonObject()
                .put("id", 1L)
                .put("username", "admin")
                .put("email", "admin@motadata.com")
                .put("roleName", "ROLE_ADMIN")
                .put("permissions", new JsonArray(List.of(
                        "ROLE_ADMIN", "ALL", "PERM_ALL",
                        "PERM_SETTINGS_READ", "PERM_SETTINGS_WRITE"
                )));

        validAdminToken = tokenService.generateToken(adminUser, 3600);

        vertx.deployVerticle(new MainVerticle(), testContext.succeedingThenComplete());

    }

    @Test
    void testUserAndMaintenanceFlow(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        // 1. List Users
        client.request(HttpMethod.GET, TEST_PORT, "localhost", "/api/user")
                .compose(req -> {

                    req.putHeader("Authorization", "Bearer " + validAdminToken);

                    return req.send();

                })
                .compose(resp -> {

                    assertEquals(200, resp.statusCode());

                    return resp.body();

                })
                .compose(body -> {

                    JsonObject json = new JsonObject(body);

                    assertTrue(json.getBoolean("success"));

                    assertNotNull(json.getJsonArray("data"));

                    // 2. List Roles
                    return client.request(HttpMethod.GET, TEST_PORT, "localhost", "/api/user/roles");

                })
                .compose(req -> {

                    req.putHeader("Authorization", "Bearer " + validAdminToken);

                    return req.send();

                })
                .compose(resp -> {

                    assertEquals(200, resp.statusCode());

                    return resp.body();

                })
                .compose(body -> {

                    JsonObject json = new JsonObject(body);

                    assertTrue(json.getBoolean("success"));

                    assertTrue(json.getJsonArray("data").size() > 0);

                    // 3. Get Database Maintenance Settings
                    return client.request(HttpMethod.GET, TEST_PORT, "localhost", "/api/database-maintenance");

                })
                .compose(req -> {

                    req.putHeader("Authorization", "Bearer " + validAdminToken);

                    return req.send();

                })
                .compose(resp -> {

                    assertEquals(200, resp.statusCode());

                    return resp.body();

                })
                .compose(body -> {

                    JsonObject json = new JsonObject(body);

                    assertTrue(json.getBoolean("success"));

                    JsonObject data = json.getJsonObject("data");

                    assertNotNull(data);

                    assertTrue(data.getInteger("maintainedDays", 30) > 0);

                    // 4. Update Database Maintenance Settings
                    return client.request(HttpMethod.PUT, TEST_PORT, "localhost", "/api/database-maintenance");

                })
                .compose(req -> {

                    req.putHeader("Authorization", "Bearer " + validAdminToken);

                    req.putHeader("Content-Type", "application/json");

                    JsonObject payload = new JsonObject()
                            .put("maintainedDays", 60)
                            .put("status", "enable")
                            .put("scheduleStatus", true);

                    return req.send(payload.encode());

                })
                .compose(resp -> {

                    assertEquals(200, resp.statusCode());

                    return resp.body();

                })
                .compose(body -> {

                    JsonObject json = new JsonObject(body);

                    assertTrue(json.getBoolean("success"));

                    // 5. Trigger Data Retention Purge
                    return client.request(HttpMethod.POST, TEST_PORT, "localhost", "/api/database-maintenance/purge");

                })
                .compose(req -> {

                    req.putHeader("Authorization", "Bearer " + validAdminToken);

                    req.putHeader("Content-Type", "application/json");

                    JsonObject payload = new JsonObject().put("maintainedDays", 60);

                    return req.send(payload.encode());

                })
                .compose(resp -> {

                    assertEquals(200, resp.statusCode());

                    return resp.body();

                })
                .compose(body -> {

                    JsonObject json = new JsonObject(body);

                    assertTrue(json.getBoolean("success"));

                    JsonObject purgeData = json.getJsonObject("data");

                    assertNotNull(purgeData);

                    assertTrue(purgeData.getBoolean("purged"));

                    return Future.succeededFuture();

                })
                .onComplete(testContext.succeeding(v -> testContext.completeNow()));

    }

}
