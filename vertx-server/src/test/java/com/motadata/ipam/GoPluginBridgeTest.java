package com.motadata.ipam;

import com.motadata.ipam.plugin.GoPluginBridge;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonObject;

import io.vertx.junit5.VertxExtension;

import io.vertx.junit5.VertxTestContext;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class GoPluginBridgeTest {

    @Test
    void testGoPluginBridge_Ping(Vertx vertx, VertxTestContext testContext) {

        GoPluginBridge bridge = new GoPluginBridge(vertx);

        String pingPayload = "{\"ip_list\":[\"127.0.0.1\"],\"max_concurrent_ping\":1,\"max-ping-check-timeout\":1000}";

        bridge.execute("ping", pingPayload)
                .onSuccess(result -> {

                    assertNotNull(result);

                    assertTrue(result.containsKey("up") || result.containsKey("down"));

                    bridge.close();

                    testContext.completeNow();

                })
                .onFailure(err -> {

                    bridge.close();

                    testContext.failNow(err);

                });

    }

}
