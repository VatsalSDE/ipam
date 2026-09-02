package com.motadata.ipam.core.config;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.yaml.snakeyaml.Yaml;

import java.io.File;

import java.io.FileInputStream;

import java.io.InputStream;

import java.util.Map;

/**
 * AppConfig manages application settings loaded from config/ipm-conf.yml
 * with fallback to environment variables and enterprise defaults.
 */
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    private int serverPort = 8080;

    private String serverHost = "0.0.0.0";

    private String dbHost = "localhost";

    private int dbPort = 3306;

    private String dbName = "ipam";

    private String dbUser = "root";

    private String dbPassword = "Mind@123";

    private int dbPoolMaxSize = 50;

    private int dbPoolMaxWaitQueue = 500;

    private int maxConcurrentPing = 500;

    private int maxPingTimeoutSec = 10;

    private int maxPingRetry = 2;

    private String jwtSecret = "MotadataTraceOrgIPAMSuperSecretKey2026";

    private int eventLoopPoolSize = Runtime.getRuntime().availableProcessors() * 2;

    private int workerPoolSize = 50;

    private int pluginWorkerPoolSize = 20;

    private int verticleInstances = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors()));

    private long blockedThreadCheckIntervalMs = 2000;

    private static AppConfig instance;

    private AppConfig() {

    }

    public static synchronized AppConfig getInstance() {


        if (instance == null) {

            instance = loadConfig();

        }

        return instance;

    }

    private static AppConfig loadConfig() {

        AppConfig config = new AppConfig();

        File configFile = new File("../config/ipm-conf.yml");

        if (!configFile.exists()) {

            configFile = new File("config/ipm-conf.yml");

        }

        if (configFile.exists()) {

            try (InputStream input = new FileInputStream(configFile)) {

                Yaml yaml = new Yaml();

                Map<String, Object> data = yaml.load(input);

                if (data != null) {

                    if (data.containsKey("server-port")) {

                        config.serverPort = Integer.parseInt(data.get("server-port").toString());

                    }

                    if (data.containsKey("server-host")) {

                        config.serverHost = data.get("server-host").toString();

                    }

                    if (data.containsKey("db-host")) {

                        config.dbHost = data.get("db-host").toString();

                    }

                    if (data.containsKey("db-port")) {

                        config.dbPort = Integer.parseInt(data.get("db-port").toString());

                    }

                    if (data.containsKey("db-pool-max-size")) {

                        config.dbPoolMaxSize = Integer.parseInt(data.get("db-pool-max-size").toString());

                    }

                    if (data.containsKey("db-pool-max-wait-queue")) {

                        config.dbPoolMaxWaitQueue = Integer.parseInt(data.get("db-pool-max-wait-queue").toString());

                    }

                    if (data.containsKey("max-concurrent-ping")) {

                        config.maxConcurrentPing = Integer.parseInt(data.get("max-concurrent-ping").toString());

                    }

                    if (data.containsKey("max-ping-check-timeout")) {

                        config.maxPingTimeoutSec = Integer.parseInt(data.get("max-ping-check-timeout").toString());

                    }

                    if (data.containsKey("max-ping-check-retry-count")) {

                        config.maxPingRetry = Integer.parseInt(data.get("max-ping-check-retry-count").toString());

                    }

                    if (data.containsKey("verticle-instances")) {

                        config.verticleInstances = Integer.parseInt(data.get("verticle-instances").toString());

                    }

                    logger.info("Loaded configuration successfully from: {}", configFile.getAbsolutePath());

                }

            } catch (Exception e) {

                logger.warn("Failed to parse config/ipm-conf.yml, using defaults: {}", e.getMessage());

            }

        } else {

            logger.warn("Configuration file config/ipm-conf.yml not found, using defaults");

        }

        // Allow environment and system property overrides
        String propPort = System.getProperty("SERVER_PORT");

        if (propPort == null || propPort.isEmpty()) {

            propPort = System.getProperty("server.port");

        }

        if (propPort == null || propPort.isEmpty()) {

            propPort = System.getenv("SERVER_PORT");

        }

        if (propPort != null && !propPort.isEmpty()) {

            config.serverPort = Integer.parseInt(propPort);

        }

        String envDbHost = System.getenv("DB_HOST");

        if (envDbHost != null && !envDbHost.isEmpty()) {

            config.dbHost = envDbHost;

        }

        String envDbPort = System.getenv("DB_PORT");

        if (envDbPort != null && !envDbPort.isEmpty()) {

            config.dbPort = Integer.parseInt(envDbPort);

        }

        String envDbUser = System.getenv("DB_USER");

        if (envDbUser != null && !envDbUser.isEmpty()) {

            config.dbUser = envDbUser;

        }

        String envDbPass = System.getenv("DB_PASSWORD");

        if (envDbPass != null) {

            config.dbPassword = envDbPass;

        }

        String envDbPoolMax = System.getenv("DB_POOL_MAX_SIZE");

        if (envDbPoolMax != null && !envDbPoolMax.isEmpty()) {

            config.dbPoolMaxSize = Integer.parseInt(envDbPoolMax);

        }

        String envDbPoolQueue = System.getenv("DB_POOL_MAX_WAIT_QUEUE");

        if (envDbPoolQueue != null && !envDbPoolQueue.isEmpty()) {

            config.dbPoolMaxWaitQueue = Integer.parseInt(envDbPoolQueue);

        }

        String envDbName = System.getenv("DB_NAME");

        if (envDbName != null && !envDbName.isEmpty()) {

            config.dbName = envDbName;

        }

        String envJwtSecret = System.getenv("JWT_SECRET");

        if (envJwtSecret != null && !envJwtSecret.isEmpty()) {

            config.jwtSecret = envJwtSecret;

        }

        String envWorkers = System.getenv("WORKER_POOL_SIZE");

        if (envWorkers != null && !envWorkers.isEmpty()) {

            config.workerPoolSize = Integer.parseInt(envWorkers);

        }

        String envInstances = System.getenv("VERTICLE_INSTANCES");

        if (envInstances != null && !envInstances.isEmpty()) {

            config.verticleInstances = Integer.parseInt(envInstances);

        }

        return config;

    }

    public static synchronized void reset() {

        instance = null;

    }

    public void setServerPort(int serverPort) {

        this.serverPort = serverPort;

    }

    public int getServerPort() {

        return serverPort;

    }

    public String getServerHost() {

        return serverHost;

    }

    public String getDbHost() {

        return dbHost;

    }

    public int getDbPort() {

        return dbPort;

    }

    public String getDbName() {

        return dbName;

    }

    public String getDbUser() {

        return dbUser;

    }

    public String getDbPassword() {

        return dbPassword;

    }

    public int getMaxConcurrentPing() {

        return maxConcurrentPing;

    }

    public int getMaxPingTimeoutSec() {

        return maxPingTimeoutSec;

    }

    public int getMaxPingRetry() {

        return maxPingRetry;

    }

    public String getJwtSecret() {

        return jwtSecret;

    }

    public int getEventLoopPoolSize() {

        return eventLoopPoolSize;

    }

    public int getWorkerPoolSize() {

        return workerPoolSize;

    }

    public int getPluginWorkerPoolSize() {

        return pluginWorkerPoolSize;

    }

    public int getVerticleInstances() {

        return verticleInstances;

    }

    public long getBlockedThreadCheckIntervalMs() {

        return blockedThreadCheckIntervalMs;

    }

    public int getDbPoolMaxSize() {

        return dbPoolMaxSize;

    }

    public void setDbPoolMaxSize(int dbPoolMaxSize) {

        this.dbPoolMaxSize = dbPoolMaxSize;

    }

    public int getDbPoolMaxWaitQueue() {

        return dbPoolMaxWaitQueue;

    }

    public void setDbPoolMaxWaitQueue(int dbPoolMaxWaitQueue) {

        this.dbPoolMaxWaitQueue = dbPoolMaxWaitQueue;

    }

}
