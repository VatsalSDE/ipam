package com.motadata.ipam;

import com.motadata.ipam.config.AppConfig;

import com.motadata.ipam.database.DatabasePool;

import com.motadata.ipam.plugin.GoPluginBridge;

import com.motadata.ipam.router.AppRouter;

import com.motadata.ipam.security.JwtTokenService;

import com.motadata.ipam.security.RbacAuthHandler;

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

        // 1. Initialize Reactive Database Pool
        mysqlPool = DatabasePool.getPool(vertx);

        // 2. Initialize Go Plugin Worker Bridge
        goPluginBridge = new GoPluginBridge(vertx);

        // 3. Initialize Security & Token Services
        com.motadata.ipam.security.JwtTokenService jwtTokenService = new com.motadata.ipam.security.JwtTokenService(vertx);

        com.motadata.ipam.security.RbacAuthHandler rbacAuthHandler = new com.motadata.ipam.security.RbacAuthHandler(jwtTokenService);

        // 4. Create and Configure Web Router using Modular AppRouter
        Router router = com.motadata.ipam.router.AppRouter.create(vertx, mysqlPool, goPluginBridge, jwtTokenService, rbacAuthHandler);

        // 5. Deploy Dedicated ScanWorkerVerticle on a Worker Pool
        DeploymentOptions workerOpts = new DeploymentOptions()
                .setWorker(true)
                .setWorkerPoolName("subnet-scanner-worker-pool")
                .setWorkerPoolSize(5);

        vertx.deployVerticle(new com.motadata.ipam.verticle.ScanWorkerVerticle(mysqlPool, goPluginBridge), workerOpts)
                .onSuccess(id -> logger.info("ScanWorkerVerticle deployed successfully on worker pool (ID: {})", id))
                .onFailure(err -> logger.error("Failed to deploy ScanWorkerVerticle: {}", err.getMessage()));

        // 6. Start HTTP Server using modern Vert.x Future API
        int port = config.getServerPort();

        HttpServer server = vertx.createHttpServer();

        server.requestHandler(router)
                .listen(port, config.getServerHost())
                .onSuccess(httpServer -> {

                    logger.info("=================================================================");

                    logger.info(" Motadata IPAM Vert.x Server started successfully!");

                    logger.info(" Listening on http://{}:{}", config.getServerHost(), port);

                    logger.info(" Health Check: http://localhost:{}/health", port);

                    logger.info("=================================================================");

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
