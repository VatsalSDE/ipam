package com.motadata.ipam.verticle;

import com.motadata.ipam.plugin.GoPluginBridge;

import com.motadata.ipam.scheduler.SubnetScanScheduler;

import com.motadata.ipam.service.ScannerService;

import io.vertx.core.AbstractVerticle;

import io.vertx.core.Promise;

import io.vertx.core.json.JsonObject;

import io.vertx.mysqlclient.MySQLPool;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

/**
 * Dedicated Background Worker Verticle for Network Scanning.
 * Runs on a dedicated Worker Thread Pool, decoupled from the HTTP Event Loop.
 * Communicates purely via the Vert.x EventBus.
 */
public class ScanWorkerVerticle extends AbstractVerticle {

    public static final String ADDRESS_SCAN_TRIGGER = "ipam.subnet.scan.trigger";

    public static final String ADDRESS_SCAN_STATUS = "ipam.subnet.scan.status";

    private static final Logger logger = LoggerFactory.getLogger(ScanWorkerVerticle.class);

    private final MySQLPool mysqlPool;

    private final GoPluginBridge goPluginBridge;

    private ScannerService scannerService;

    private SubnetScanScheduler subnetScanScheduler;

    public ScanWorkerVerticle(MySQLPool mysqlPool, GoPluginBridge goPluginBridge) {

        this.mysqlPool = mysqlPool;

        this.goPluginBridge = goPluginBridge;

    }

    @Override
    public void start(Promise<Void> startPromise) {

        logger.info("Initializing ScanWorkerVerticle on worker thread: {}", Thread.currentThread().getName());

        this.scannerService = new ScannerService(mysqlPool, goPluginBridge, vertx);

        // 1. Register EventBus Consumer: On-Demand Scan Execution
        vertx.eventBus().<JsonObject>consumer(ADDRESS_SCAN_TRIGGER, message -> {

            JsonObject body = message.body();

            if (body == null || !body.containsKey("subnetId")) {

                message.fail(400, "Missing subnetId parameter");

                return;

            }

            Long subnetId = body.getLong("subnetId");

            logger.info("ScanWorkerVerticle received scan job for Subnet ID: {}", subnetId);

            scannerService.triggerScan(subnetId)
                    .onSuccess(message::reply)
                    .onFailure(err -> message.fail(500, err.getMessage()));

        });

        // 2. Register EventBus Consumer: Query Scan Status
        vertx.eventBus().<JsonObject>consumer(ADDRESS_SCAN_STATUS, message -> {

            JsonObject body = message.body();

            if (body == null || !body.containsKey("subnetId") || body.getLong("subnetId") == null || body.getLong("subnetId") <= 0) {

                JsonObject any = scannerService.getAnyActiveScan();

                message.reply(any != null ? any : new JsonObject().put("status", "IDLE"));

                return;

            }

            Long subnetId = body.getLong("subnetId");

            JsonObject status = scannerService.getScanStatus(subnetId);

            message.reply(status);

        });

        // 3. Initialize and start Periodic Subnet Scan Scheduler (checks every 60s)
        this.subnetScanScheduler = new SubnetScanScheduler(vertx, mysqlPool, scannerService);

        this.subnetScanScheduler.start(60000);

        logger.info("ScanWorkerVerticle registered EventBus consumers on [{}] and [{}]",
                ADDRESS_SCAN_TRIGGER, ADDRESS_SCAN_STATUS);

        startPromise.complete();

    }

    @Override
    public void stop(Promise<Void> stopPromise) {

        logger.info("Stopping ScanWorkerVerticle...");

        if (subnetScanScheduler != null) {

            subnetScanScheduler.stop();

        }

        stopPromise.complete();

    }

}
