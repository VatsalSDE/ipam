package com.motadata.ipam;


import com.motadata.ipam.core.AppRouter;
import com.motadata.ipam.core.config.AppConfig;
import com.motadata.ipam.core.database.DatabasePool;
import com.motadata.ipam.scanner.GoPluginBridge;
import com.motadata.ipam.scanner.ScanWorkerVerticle;
import com.motadata.ipam.security.JwtTokenService;
import com.motadata.ipam.security.RbacAuthHandler;
import com.motadata.ipam.subnet.SubnetWorkerVerticle;

import io.vertx.core.AbstractVerticle;

import io.vertx.core.DeploymentOptions;

import io.vertx.core.Promise;

import io.vertx.core.Vertx;

import io.vertx.core.VertxOptions;

import io.vertx.core.http.HttpServer;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.Router;

import io.vertx.mysqlclient.MySQLPool;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * MainVerticle is the primary entry point for the Vert.x IPAM Server.
 */
public class MainVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    private MySQLPool mysqlPool;

    private GoPluginBridge goPluginBridge;

    @Override
    public void start(Promise<Void> startPromise) {

        AppConfig config = AppConfig.getInstance();

        logger.info("Initializing Vert.x IPAM Server instance...");

        // 1. Initialize Database & Native Plugin Bridge
        this.mysqlPool = DatabasePool.getPool(vertx);

        this.goPluginBridge = new GoPluginBridge(vertx);

        // 2. Initialize Security & Token Services
        JwtTokenService jwtTokenService = new JwtTokenService(vertx);

        RbacAuthHandler rbacAuthHandler = new RbacAuthHandler(jwtTokenService);

        // 3. Assemble Web Router (Auth, Subnet, Health endpoints)
        Router router = AppRouter.create(vertx, mysqlPool, goPluginBridge, jwtTokenService, rbacAuthHandler);

        // 4. Deploy Dedicated Background Worker Verticles (Singleton across all instances) so that the worker verticles are deployed only once
        boolean isFirstInstance = vertx.sharedData().getLocalMap("ipam.system").putIfAbsent("workers.deployed", Boolean.TRUE) == null;

        if (isFirstInstance) {

            deployWorkerVerticles();

        }

        // 5. Start HTTP Server
        startHttpServer(router, config, startPromise);

    }

    /**
     * Deploys heavy background tasks (scanners, schedulers, IP populator) to dedicated worker thread pools.
     * Guaranteed to be deployed once as singletons across all MainVerticle instances.
     */
    private void deployWorkerVerticles() {

        DeploymentOptions scannerOpts = new DeploymentOptions()
                .setWorker(true)
                .setWorkerPoolName("subnet-scanner-worker-pool")
                .setWorkerPoolSize(5);

        vertx.deployVerticle(new ScanWorkerVerticle(mysqlPool, goPluginBridge), scannerOpts)
                .onSuccess(id -> logger.info("ScanWorkerVerticle deployed successfully on worker pool (ID: {})", id))
                .onFailure(err -> logger.error("Failed to deploy ScanWorkerVerticle: {}", err.getMessage()));

        DeploymentOptions subnetOpts = new DeploymentOptions()
                .setWorker(true)
                .setWorkerPoolName("subnet-ops-worker-pool")
                .setWorkerPoolSize(5);

        vertx.deployVerticle(new SubnetWorkerVerticle(mysqlPool), subnetOpts)
                .onSuccess(id -> logger.info("SubnetWorkerVerticle deployed successfully on worker pool (ID: {})", id))
                .onFailure(err -> logger.error("Failed to deploy SubnetWorkerVerticle: {}", err.getMessage()));

    }

    /**
     * Starts the non-blocking HTTP server listening on the configured host and port.
     */
    private void startHttpServer(Router router, AppConfig config, Promise<Void> startPromise) {

        int port = config.getServerPort();

        String host = config.getServerHost();

        HttpServer server = vertx.createHttpServer();

        server.requestHandler(router)
                .listen(port, host)
                .onSuccess(httpServer -> {

                    boolean firstInstance = vertx.sharedData()
                            .getLocalMap("ipam.system")
                            .putIfAbsent("banner.printed", Boolean.TRUE) == null;

                    if (firstInstance) {

                        logger.info("=================================================================");

                        logger.info(" Motadata IPAM Vert.x Server started successfully!");

                        logger.info(" Listening on http://{}:{}", host, port);

                        logger.info(" Health Check: http://localhost:{}/health", port);

                        logger.info("=================================================================");

                    }

                    startPromise.complete();

                })
                .onFailure(err -> {

                    logger.error("Failed to start HTTP server on port {}: {}", port, err.getMessage());

                    startPromise.fail(err);

                });

    }

    @Override
    public void stop(Promise<Void> stopPromise) {

        logger.info("Stopping Motadata IPAM Vert.x Server instance...");

        if (goPluginBridge != null) {

            goPluginBridge.close();

        }

        DatabasePool.close();

        stopPromise.complete();

    }

    public static void main(String[] args) {

        AppConfig config = AppConfig.getInstance();

        // 1. Enterprise VertxOptions (Global Thread Pools & Monitoring)
        VertxOptions vertxOptions = new VertxOptions()
                .setEventLoopPoolSize(config.getEventLoopPoolSize())
                .setWorkerPoolSize(config.getWorkerPoolSize())
                .setMaxEventLoopExecuteTime(config.getBlockedThreadCheckIntervalMs())
                .setMaxEventLoopExecuteTimeUnit(TimeUnit.MILLISECONDS)
                .setBlockedThreadCheckInterval(config.getBlockedThreadCheckIntervalMs());

        Vertx vertx = Vertx.vertx(vertxOptions);

        // 2. Enterprise DeploymentOptions (Multi-core Instance Scaling)
        DeploymentOptions deploymentOptions = new DeploymentOptions()
                .setInstances(config.getVerticleInstances())
                .setConfig(new JsonObject()
                        .put("server.port", config.getServerPort())
                        .put("server.host", config.getServerHost()));

        logger.info("Deploying {} verticle instances across {} event loops and {} worker threads...",
                config.getVerticleInstances(), config.getEventLoopPoolSize(), config.getWorkerPoolSize());

        vertx.deployVerticle(MainVerticle.class.getName(), deploymentOptions)
                .onSuccess(deploymentId -> {

                    logger.info("All {} instances deployed successfully with Deployment ID: {}",
                            config.getVerticleInstances(), deploymentId);
                })

                .onFailure(err -> {

                    logger.error("Deployment failed: {}", err.getMessage());

                });

    }

}
