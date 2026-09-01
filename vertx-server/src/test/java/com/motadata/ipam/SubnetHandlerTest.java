package com.motadata.ipam;

import com.motadata.ipam.config.AppConfig;

import com.motadata.ipam.security.JwtTokenService;

import io.vertx.core.Vertx;

import io.vertx.core.buffer.Buffer;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration and route protection tests for SubnetRouter and SubnetHandler.
 */
@ExtendWith(VertxExtension.class)
class SubnetHandlerTest {

    private static final int TEST_PORT = 8890;

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
                .put("permissions", new JsonArray(List.of("ROLE_ADMIN", "ALL", "PERM_ALL", "PERM_SUBNET_VIEW", "PERM_SUBNET_EDIT")));

        validAdminToken = tokenService.generateToken(adminUser, 3600);

        vertx.deployVerticle(new MainVerticle(), testContext.succeedingThenComplete());

    }

    @Test
    void testSubnetEndpointsRequireAuthentication(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        // 1. GET /api/subnet without token -> 401 UNAUTHORIZED
        client.request(HttpMethod.GET, TEST_PORT, "localhost", "/api/subnet")
                .compose(req -> req.send())
                .onComplete(testContext.succeeding(resp -> {

                    assertEquals(401, resp.statusCode());

                    testContext.completeNow();

                }));

    }

    @Test
    void testSubnetCheckInstantValidation(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        JsonObject payload = new JsonObject()
                .put("subnetAddress", "192.168.100.45")
                .put("subnetCidr", 24);

        client.request(HttpMethod.POST, TEST_PORT, "localhost", "/api/subnet/check")
                .compose(req -> {

                    req.putHeader("Content-Type", "application/json");

                    req.putHeader("Authorization", "Bearer " + validAdminToken);

                    return req.send(Buffer.buffer(payload.encode()));

                })
                .compose(resp -> {

                    assertEquals(200, resp.statusCode());

                    return resp.body();

                })
                .onComplete(testContext.succeeding(body -> {

                    JsonObject json = new JsonObject(body.toString());

                    assertTrue(json.getBoolean("success"));

                    JsonObject data = json.getJsonObject("data");

                    assertNotNull(data);

                    // Normalizes 192.168.100.45 -> 192.168.100.0
                    assertEquals("192.168.100.0", data.getString("normalizedAddress"));

                    assertEquals("255.255.255.0", data.getString("subnetMask"));

                    assertEquals(254L, data.getLong("totalIp"));

                    assertTrue(data.getBoolean("isValid"));

                    testContext.completeNow();

                }));

    }

    @Test
    void testSubnetCheckLegacyMaskInfoFormat(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        // Testing the legacy Firefox modal format: "maskInfo": "255.255.255.0/24"
        JsonObject payload = new JsonObject()
                .put("subnetAddress", "10.0.5.12")
                .put("maskInfo", "255.255.255.0/24");

        client.request(HttpMethod.POST, TEST_PORT, "localhost", "/api/subnet/check")
                .compose(req -> {

                    req.putHeader("Content-Type", "application/json");

                    req.putHeader("Authorization", "Bearer " + validAdminToken);

                    return req.send(Buffer.buffer(payload.encode()));

                })
                .compose(resp -> {

                    assertEquals(200, resp.statusCode());

                    return resp.body();

                })
                .onComplete(testContext.succeeding(body -> {

                    JsonObject json = new JsonObject(body.toString());

                    assertTrue(json.getBoolean("success"));

                    JsonObject data = json.getJsonObject("data");

                    assertEquals("10.0.5.0", data.getString("normalizedAddress"));

                    assertEquals("255.255.255.0", data.getString("subnetMask"));

                    assertEquals(254L, data.getLong("totalIp"));

                    testContext.completeNow();

                }));

    }

    @Test
    void testSubnetCheckInvalidInputs(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        JsonObject invalidPayload = new JsonObject()
                .put("subnetAddress", "999.999.999.999")
                .put("subnetCidr", 40);

        client.request(HttpMethod.POST, TEST_PORT, "localhost", "/api/subnet/check")
                .compose(req -> {

                    req.putHeader("Content-Type", "application/json");

                    req.putHeader("Authorization", "Bearer " + validAdminToken);

                    return req.send(Buffer.buffer(invalidPayload.encode()));

                })
                .compose(resp -> {

                    assertEquals(400, resp.statusCode());

                    return resp.body();

                })
                .onComplete(testContext.succeeding(body -> {

                    JsonObject json = new JsonObject(body.toString());

                    assertFalse(json.getBoolean("success"));

                    assertEquals("BAD_REQUEST", json.getString("errorCode"));

                    testContext.completeNow();

                }));

    }

    @Test
    void testSubnetScanRequiresAuth(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        client.request(HttpMethod.POST, TEST_PORT, "localhost", "/api/subnet/1/scan")
                .compose(req -> req.send())
                .onComplete(testContext.succeeding(resp -> {

                    assertEquals(401, resp.statusCode());

                    testContext.completeNow();

                }));

    }

    @Test
    void testSubnetScanStatusEndpoint(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        client.request(HttpMethod.GET, TEST_PORT, "localhost", "/api/subnet/1/scan-status")
                .compose(req -> {

                    req.putHeader("Authorization", "Bearer " + validAdminToken);

                    return req.send();

                })
                .compose(resp -> {

                    assertEquals(200, resp.statusCode());

                    return resp.body();

                })
                .onComplete(testContext.succeeding(body -> {

                    JsonObject json = new JsonObject(body.toString());

                    assertTrue(json.getBoolean("success"));

                    JsonObject data = json.getJsonObject("data");

                    assertNotNull(data);

                    assertEquals("IDLE", data.getString("status"));

                    testContext.completeNow();

                }));

    }

}
