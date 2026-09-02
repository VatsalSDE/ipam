package com.motadata.ipam;


import com.motadata.ipam.MainVerticle;
import com.motadata.ipam.core.config.AppConfig;
import com.motadata.ipam.core.database.DatabasePool;
import com.motadata.ipam.security.JwtTokenService;
import com.motadata.ipam.security.PasswordUtil;

import io.vertx.core.Vertx;

import io.vertx.core.buffer.Buffer;

import io.vertx.core.http.HttpClient;

import io.vertx.core.http.HttpMethod;

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
public class AuthHandlerTest {

    private static final int TEST_PORT = 8889;

    @BeforeEach
    void deployVerticle(Vertx vertx, VertxTestContext testContext) {

        AppConfig.reset();

        com.motadata.ipam.core.database.DatabasePool.close();

        AppConfig.getInstance().setServerPort(TEST_PORT);

        vertx.deployVerticle(new MainVerticle(), testContext.succeedingThenComplete());

    }

    @Test
    void testPasswordUtilBCrypt() {

        String plain = "Admin@123";

        String hash = PasswordUtil.hash(plain);

        assertNotNull(hash);

        assertTrue(hash.startsWith("$pbkdf2$") || hash.startsWith("$2a$") || hash.startsWith("$2b$"));

        assertTrue(PasswordUtil.verify(plain, hash));

        assertTrue(!PasswordUtil.verify("WrongPassword", hash));

    }

    @Test
    void testJwtTokenGenerationAndValidation(Vertx vertx, VertxTestContext testContext) {

        JwtTokenService tokenService = new JwtTokenService(vertx);

        JsonObject user = new JsonObject()
                .put("id", 1L)
                .put("username", "admin")
                .put("email", "admin@motadata.com")
                .put("roleName", "ROLE_ADMIN")
                .put("permissions", new io.vertx.core.json.JsonArray(List.of("PERM_DASHBOARD_READ", "PERM_SUBNET_VIEW")));

        String token = tokenService.generateToken(user, 3600);

        assertNotNull(token);

        tokenService.verifyToken(token)
                .onComplete(testContext.succeeding(principal -> {

                    assertEquals("admin", principal.getString("sub"));

                    assertEquals(1L, principal.getLong("userId"));

                    assertEquals("ROLE_ADMIN", principal.getString("roleName"));

                    testContext.completeNow();

                }));

    }

    @Test
    void testLoginSuccessAndProtectedMeEndpoint(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        JsonObject loginPayload = new JsonObject()
                .put("username", "admin")
                .put("password", "admin");

        client.request(HttpMethod.POST, TEST_PORT, "localhost", "/api/auth/login")
                .compose(req -> {

                    req.putHeader("Content-Type", "application/json");

                    return req.send(Buffer.buffer(loginPayload.encode()));

                })
                .compose(resp -> {

                    assertEquals(200, resp.statusCode());

                    return resp.body();

                })
                .onComplete(testContext.succeeding(body -> {

                    JsonObject resJson = new JsonObject(body.toString());

                    assertTrue(resJson.getBoolean("success"));

                    JsonObject data = resJson.getJsonObject("data");

                    assertNotNull(data);

                    String token = data.getString("access_token");

                    assertNotNull(token);

                    // Test accessing protected /api/auth/me with Bearer token
                    client.request(HttpMethod.GET, TEST_PORT, "localhost", "/api/auth/me")
                            .compose(req -> {

                                req.putHeader("Authorization", "Bearer " + token);

                                return req.send();

                            })
                            .compose(resp -> {

                                assertEquals(200, resp.statusCode());

                                return resp.body();

                            })
                            .onComplete(testContext.succeeding(meBody -> {

                                JsonObject meJson = new JsonObject(meBody.toString());

                                assertTrue(meJson.getBoolean("success"));

                                JsonObject userData = meJson.getJsonObject("data");

                                assertEquals("admin", userData.getString("username"));

                                testContext.completeNow();

                            }));

                }));

    }

    @Test
    void testLoginInvalidCredentials(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        JsonObject loginPayload = new JsonObject()
                .put("username", "admin")
                .put("password", "completely_wrong_password");

        client.request(HttpMethod.POST, TEST_PORT, "localhost", "/api/auth/login")
                .compose(req -> {

                    req.putHeader("Content-Type", "application/json");

                    return req.send(Buffer.buffer(loginPayload.encode()));

                })
                .compose(resp -> {

                    assertEquals(401, resp.statusCode());

                    return resp.body();

                })
                .onComplete(testContext.succeeding(body -> {

                    JsonObject resJson = new JsonObject(body.toString());

                    assertEquals(false, resJson.getBoolean("success"));

                    assertEquals("BAD_CREDENTIALS", resJson.getString("errorCode"));

                    testContext.completeNow();

                }));

    }

    @Test
    void testProtectedMeEndpointWithoutToken(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        client.request(HttpMethod.GET, TEST_PORT, "localhost", "/api/auth/me")
                .compose(req -> req.send())
                .compose(resp -> {

                    assertEquals(401, resp.statusCode());

                    return resp.body();

                })
                .onComplete(testContext.succeeding(body -> {

                    JsonObject resJson = new JsonObject(body.toString());

                    assertEquals(false, resJson.getBoolean("success"));

                    assertEquals("UNAUTHORIZED", resJson.getString("errorCode"));

                    testContext.completeNow();

                }));

    }

    @Test
    void testRefreshTokenFlow(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        JsonObject loginPayload = new JsonObject()
                .put("username", "admin")
                .put("password", "admin");

        client.request(HttpMethod.POST, TEST_PORT, "localhost", "/api/auth/login")
                .compose(req -> {

                    req.putHeader("Content-Type", "application/json");

                    return req.send(Buffer.buffer(loginPayload.encode()));

                })
                .compose(resp -> {

                    assertEquals(200, resp.statusCode());

                    return resp.body();

                })
                .onComplete(testContext.succeeding(body -> {

                    JsonObject resJson = new JsonObject(body.toString());

                    JsonObject data = resJson.getJsonObject("data");

                    String refreshToken = data.getString("refresh_token");

                    assertNotNull(refreshToken);

                    // Call /api/auth/refresh with refresh token
                    JsonObject refreshPayload = new JsonObject().put("refresh_token", refreshToken);

                    client.request(HttpMethod.POST, TEST_PORT, "localhost", "/api/auth/refresh")
                            .compose(req -> {

                                req.putHeader("Content-Type", "application/json");

                                return req.send(Buffer.buffer(refreshPayload.encode()));

                            })
                            .compose(resp -> {

                                assertEquals(200, resp.statusCode());

                                return resp.body();

                            })
                            .onComplete(testContext.succeeding(refreshBody -> {

                                JsonObject refreshRes = new JsonObject(refreshBody.toString());

                                assertTrue(refreshRes.getBoolean("success"));

                                String newAccessToken = refreshRes.getJsonObject("data").getString("access_token");

                                assertNotNull(newAccessToken);

                                testContext.completeNow();

                            }));

                }));

    }

}
