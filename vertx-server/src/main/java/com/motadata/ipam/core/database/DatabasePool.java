package com.motadata.ipam.core.database;

import com.motadata.ipam.core.config.AppConfig;
import io.vertx.core.Vertx;
import io.vertx.mysqlclient.MySQLBuilder;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DatabasePool manages reactive non-blocking connection pooling to MariaDB/MySQL in Vert.x 5.
 */
public class DatabasePool {

    private static final Logger logger = LoggerFactory.getLogger(DatabasePool.class);
    private static Pool pool;

    public static synchronized Pool getPool(Vertx vertx) {
        if (pool == null) {
            AppConfig config = AppConfig.getInstance();

            MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                    .setHost(config.getDbHost())
                    .setPort(config.getDbPort())
                    .setDatabase(config.getDbName())
                    .setUser(config.getDbUser())
                    .setPassword(config.getDbPassword());

            PoolOptions poolOptions = new PoolOptions()
                    .setMaxSize(config.getDbPoolMaxSize())
                    .setMaxWaitQueueSize(config.getDbPoolMaxWaitQueue());

            // Vert.x 5 Fluent Builder:
            pool = MySQLBuilder.pool()
                    .connectingTo(connectOptions)
                    .with(poolOptions)
                    .using(vertx)
                    .build();

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