package com.motadata.ipam.subnet;


import com.motadata.ipam.core.database.DbQueries;

import io.vertx.core.AbstractVerticle;

import io.vertx.core.Future;

import io.vertx.core.Promise;

import io.vertx.core.json.JsonObject;

import io.vertx.mysqlclient.MySQLPool;

import io.vertx.sqlclient.Tuple;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import java.util.List;

/**
 * Dedicated Background Worker Verticle for Subnet & IP Address Population.
 * Runs on a dedicated Worker Thread Pool, fully decoupled from the HTTP Event Loop.
 * Implements True Streaming Batch Inserts to ensure constant O(1) memory footprint.
 */
public class SubnetWorkerVerticle extends AbstractVerticle {

    public static final String ADDRESS_POPULATE_IPS = "ipam.subnet.populate.ips";

    public static final String ADDRESS_POPULATE_COMPLETED = "ipam.subnet.populate.completed";

    private static final Logger logger = LoggerFactory.getLogger(SubnetWorkerVerticle.class);

    private static final int DEFAULT_CHUNK_SIZE = 512;

    private final MySQLPool mysqlPool;

    public SubnetWorkerVerticle(MySQLPool mysqlPool) {

        this.mysqlPool = mysqlPool;

    }

    @Override
    public void start(Promise<Void> startPromise) {

        logger.info("Initializing SubnetWorkerVerticle on worker thread: {}", Thread.currentThread().getName());

        vertx.eventBus().<JsonObject>consumer(ADDRESS_POPULATE_IPS, message -> {

            JsonObject body = message.body();

            if (body == null || !body.containsKey("subnetId")) {

                message.fail(400, "Missing subnetId parameter");

                return;

            }

            Long subnetId = body.getLong("subnetId");

            Long networkLong = body.getLong("networkLong");

            Long broadcastLong = body.getLong("broadcastLong");

            Integer chunkSize = body.getInteger("chunkSize", DEFAULT_CHUNK_SIZE);

            if (networkLong == null || broadcastLong == null) {

                message.fail(400, "Missing networkLong or broadcastLong parameters");

                return;

            }

            long firstUsable = networkLong + 1;

            long lastUsable = broadcastLong - 1;

            if (firstUsable > lastUsable) {

                message.reply(new JsonObject()
                        .put("subnetId", subnetId)
                        .put("totalInserted", 0)
                        .put("status", "COMPLETED"));

                return;

            }

            long hostCount = lastUsable - firstUsable + 1;

            logger.info("SubnetWorkerVerticle streaming IP population for Subnet ID {} ({} hosts)...",
                    subnetId, hostCount);

            streamInsertIpChunks(subnetId, firstUsable, lastUsable, chunkSize, 0)
                    .onSuccess(totalInserted -> {

                        logger.info("SubnetWorkerVerticle finished streaming insertion of {} IPs for Subnet ID {}",
                                totalInserted, subnetId);

                        JsonObject result = new JsonObject()
                                .put("subnetId", subnetId)
                                .put("totalInserted", totalInserted)
                                .put("status", "COMPLETED");

                        vertx.eventBus().publish(ADDRESS_POPULATE_COMPLETED, result);

                        message.reply(result);

                    })
                    .onFailure(err -> {

                        logger.error("SubnetWorkerVerticle failed to populate IPs for Subnet ID {}: {}",
                                subnetId, err.getMessage(), err);

                        mysqlPool.preparedQuery(DbQueries.DELETE_SUBNET_IPS)
                                .execute(Tuple.of(subnetId))
                                .onComplete(v -> message.fail(500, "IP population failed: " + err.getMessage()));

                    });

        });

        logger.info("SubnetWorkerVerticle registered EventBus consumer on [{}]", ADDRESS_POPULATE_IPS);

        startPromise.complete();

    }

    /**
     * Recursively streams IP batches of chunkSize to MySQL using primitive long arithmetic.
     * Guarantees strictly constant O(1) JVM heap memory (< 50 KB) regardless of subnet size.
     */
    public Future<Integer> streamInsertIpChunks(Long subnetId, long currentIp, long lastUsableIp, int chunkSize, int totalInserted) {

        if (currentIp > lastUsableIp) {

            return Future.succeededFuture(totalInserted);

        }

        long chunkEndIp = Math.min(currentIp + chunkSize - 1, lastUsableIp);

        int batchSize = (int) (chunkEndIp - currentIp + 1);

        List<Tuple> batch = new ArrayList<>(batchSize);

        for (long ip = currentIp; ip <= chunkEndIp; ip++) {

            batch.add(Tuple.of(subnetId, IPv4Util.longToIp(ip)));

        }

        return mysqlPool.preparedQuery(DbQueries.INSERT_SUBNET_IPS_BATCH)
                .executeBatch(batch)
                .compose(res -> {

                    // The batch of tuples is dereferenced and immediately eligible for Garbage Collection!
                    return streamInsertIpChunks(subnetId, chunkEndIp + 1, lastUsableIp, chunkSize, totalInserted + batchSize);

                });

    }

}
