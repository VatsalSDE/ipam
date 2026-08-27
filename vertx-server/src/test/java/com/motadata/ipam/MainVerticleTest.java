package com.motadata.ipam;

import io.vertx.core.Vertx;

import io.vertx.core.http.HttpClient;

import io.vertx.core.http.HttpMethod;

import io.vertx.core.json.JsonObject;

import io.vertx.junit5.VertxExtension;

import io.vertx.junit5.VertxTestContext;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(VertxExtension.class)
public class MainVerticleTest {

    @BeforeEach
    void deployVerticle(Vertx vertx, VertxTestContext testContext) {

        com.motadata.ipam.config.AppConfig.reset();

        com.motadata.ipam.config.AppConfig.getInstance().setServerPort(8888);

        vertx.deployVerticle(new MainVerticle(), testContext.succeedingThenComplete());

    }

    @Test
    void testHealthEndpoint(Vertx vertx, VertxTestContext testContext) {

        HttpClient client = vertx.createHttpClient();

        client.request(HttpMethod.GET, 8888, "localhost", "/health")
                .compose(req -> req.send().compose(resp -> {

                    assertEquals(200, resp.statusCode());

                    return resp.body();

                }))
                .onComplete(testContext.succeeding(body -> {

                    JsonObject json = new JsonObject(body.toString());

                    assertEquals(true, json.getBoolean("success"));

                    assertNotNull(json.getJsonObject("data"));

                    assertEquals("UP", json.getJsonObject("data").getString("status"));

                    testContext.completeNow();

                }));

    }

}
