package com.motadata.ipam;


import com.motadata.ipam.MainVerticle;
import com.motadata.ipam.core.config.AppConfig;
import com.motadata.ipam.core.database.DatabasePool;

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
    void setup() {

        com.motadata.ipam.core.database.DatabasePool.close();

        com.motadata.ipam.core.config.AppConfig.reset();

        com.motadata.ipam.core.config.AppConfig.getInstance().setServerPort(8899);

    }

    @Test
    void testServerEndpoints(Vertx vertx, VertxTestContext testContext) {

        vertx.deployVerticle(new MainVerticle())
                .compose(deployId -> {

                    HttpClient client = vertx.createHttpClient();

                    return client.request(HttpMethod.GET, 8899, "localhost", "/health")
                            .compose(req -> req.send().compose(resp -> {

                                assertEquals(200, resp.statusCode());

                                return resp.body();

                            }))
                            .compose(body -> {

                                JsonObject json = new JsonObject(body.toString());

                                assertEquals(true, json.getBoolean("success"));

                                assertNotNull(json.getJsonObject("data"));

                                assertEquals("UP", json.getJsonObject("data").getString("status"));

                                return client.request(HttpMethod.GET, 8899, "localhost", "/login.html");

                            })
                            .compose(req -> req.send().compose(resp -> {

                                assertEquals(200, resp.statusCode());

                                return client.request(HttpMethod.GET, 8899, "localhost", "/home.html");

                            }))
                            .compose(req -> req.send().compose(resp -> {

                                assertEquals(200, resp.statusCode());

                                return client.request(HttpMethod.GET, 8899, "localhost", "/");

                            }))
                            .compose(req -> req.send().compose(resp -> {

                                assertEquals(200, resp.statusCode());

                                return io.vertx.core.Future.succeededFuture();

                            }));

                })
                .onComplete(testContext.succeedingThenComplete());

    }

}
