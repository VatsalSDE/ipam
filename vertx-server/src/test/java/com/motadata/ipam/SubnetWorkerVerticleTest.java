package com.motadata.ipam;


import com.motadata.ipam.core.config.AppConfig;
import com.motadata.ipam.core.database.DatabasePool;
import com.motadata.ipam.subnet.IPv4Util;
import com.motadata.ipam.subnet.SubnetWorkerVerticle;

import io.vertx.core.DeploymentOptions;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonObject;

import io.vertx.junit5.VertxExtension;

import io.vertx.junit5.VertxTestContext;

import io.vertx.mysqlclient.MySQLPool;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class SubnetWorkerVerticleTest {

    private MySQLPool mysqlPool;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {

        AppConfig.reset();

        DatabasePool.close();

        this.mysqlPool = DatabasePool.getPool(vertx);

        DeploymentOptions workerOpts = new DeploymentOptions()
                .setWorker(true)
                .setWorkerPoolName("subnet-ops-test-worker-pool")
                .setWorkerPoolSize(2);

        vertx.deployVerticle(new SubnetWorkerVerticle(mysqlPool), workerOpts, testContext.succeedingThenComplete());

    }

    @Test
    void testSubnetWorkerRejectsMissingParameters(Vertx vertx, VertxTestContext testContext) {

        JsonObject invalidBody = new JsonObject();

        vertx.eventBus().<JsonObject>request(SubnetWorkerVerticle.ADDRESS_POPULATE_IPS, invalidBody, reply -> {

            assertTrue(reply.failed());

            testContext.completeNow();

        });

    }

    @Test
    void testSubnetWorkerEmptyRange(Vertx vertx, VertxTestContext testContext) {

        long net = IPv4Util.ipToLong("192.168.1.0");

        JsonObject body = new JsonObject()
                .put("subnetId", 999999L)
                .put("networkLong", net)
                .put("broadcastLong", net + 1);

        vertx.eventBus().<JsonObject>request(SubnetWorkerVerticle.ADDRESS_POPULATE_IPS, body, testContext.succeeding(msg -> {

            JsonObject reply = msg.body();

            assertNotNull(reply);

            assertEquals("COMPLETED", reply.getString("status"));

            assertEquals(0, reply.getInteger("totalInserted"));

            testContext.completeNow();

        }));

    }

}
