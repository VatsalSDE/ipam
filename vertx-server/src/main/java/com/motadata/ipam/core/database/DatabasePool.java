package com.motadata.ipam.core.database;


import com.motadata.ipam.core.config.AppConfig;

import io.vertx.core.Vertx;

import io.vertx.mysqlclient.MySQLConnectOptions;

import io.vertx.mysqlclient.MySQLPool;

import io.vertx.sqlclient.PoolOptions;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

/**
 * DatabasePool manages reactive non-blocking connection pooling to MariaDB/MySQL.
 */
public class DatabasePool {

    private static final Logger logger = LoggerFactory.getLogger(DatabasePool.class);

    private static MySQLPool pool;

    public static synchronized MySQLPool getPool(Vertx vertx) {

        if (pool == null) {

            AppConfig config = AppConfig.getInstance();

            MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                    .setHost(config.getDbHost())
                    .setPort(config.getDbPort())
                    .setDatabase(config.getDbName())
                    .setUser(config.getDbUser())
                    .setPassword(config.getDbPassword())
                    .setConnectTimeout(5000);

            PoolOptions poolOptions = new PoolOptions()
                    .setMaxSize(config.getDbPoolMaxSize())
                    .setMaxWaitQueueSize(config.getDbPoolMaxWaitQueue());

            pool = MySQLPool.pool(vertx, connectOptions, poolOptions);

            logger.info("Initialized Reactive MySQL Pool connecting to {}:{}/{} (maxSize={}, maxWaitQueue={})",
                    config.getDbHost(), config.getDbPort(), config.getDbName(),
                    config.getDbPoolMaxSize(), config.getDbPoolMaxWaitQueue());

        }

        return pool;

    }

    public static synchronized void close() {

        if (pool != null) {

            pool.close();

            pool = null;

            logger.info("Closed Reactive MySQL Pool");

        }

    }

}
