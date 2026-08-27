package com.motadata.ipam.scheduler;

import com.motadata.ipam.database.DbQueries;

import com.motadata.ipam.service.ScannerService;

import io.vertx.core.Vertx;

import io.vertx.mysqlclient.MySQLPool;

import io.vertx.sqlclient.Row;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.time.Instant;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-blocking Periodic Subnet Scan Scheduler.
 * Replaces legacy heavy Quartz scheduler with native Vert.x periodic timers.
 * Inspects subnets configured with scheduleStatus = 1 and triggers automated sweeps.
 */
public class SubnetScanScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SubnetScanScheduler.class);

    private final Vertx vertx;

    private final MySQLPool mysqlPool;

    private final ScannerService scannerService;

    // Subnet ID -> Last scanned timestamp in milliseconds
    private final Map<Long, Long> lastScannedMap = new ConcurrentHashMap<>();

    private Long periodicTimerId;

    public SubnetScanScheduler(Vertx vertx, MySQLPool mysqlPool, ScannerService scannerService) {

        this.vertx = vertx;

        this.mysqlPool = mysqlPool;

        this.scannerService = scannerService;

    }

    /**
     * Starts the periodic scheduler. Checks scheduled subnets every pollIntervalMs.
     */
    public void start(long pollIntervalMs) {

        if (periodicTimerId != null) {

            return;

        }

        logger.info("Starting SubnetScanScheduler with polling interval: {} ms", pollIntervalMs);

        periodicTimerId = vertx.setPeriodic(pollIntervalMs, id -> checkAndTriggerScheduledScans());

    }

    /**
     * Stops the periodic scheduler.
     */
    public void stop() {

        if (periodicTimerId != null) {

            vertx.cancelTimer(periodicTimerId);

            periodicTimerId = null;

            logger.info("SubnetScanScheduler stopped");

        }

    }

    /**
     * Queries database for active schedules and triggers scans if due.
     */
    public void checkAndTriggerScheduledScans() {

        String sql = DbQueries.SCHEDULER_FIND_DUE_SUBNETS;

        mysqlPool.preparedQuery(sql).execute()
                .onSuccess(rows -> {

                    long now = Instant.now().toEpochMilli();

                    for (Row row : rows) {

                        Long subnetId = row.getLong("id");

                        String subnetName = row.getString("subnetName");

                        Integer scheduleHour = row.getInteger("scheduleHour");

                        if (scheduleHour == null || scheduleHour <= 0) {

                            scheduleHour = 24; // Default to daily if not set

                        }

                        long intervalMs = (long) scheduleHour * 3600 * 1000;

                        Long lastScanned = lastScannedMap.get(subnetId);

                        if (lastScanned == null || (now - lastScanned) >= intervalMs) {

                            logger.info("Subnet '{}' (ID: {}) is due for scheduled scan (every {} hours)", subnetName, subnetId, scheduleHour);

                            lastScannedMap.put(subnetId, now);

                            scannerService.triggerScan(subnetId)
                                    .onSuccess(res -> logger.info("Scheduled scan completed for subnet '{}': {} UP", subnetName, res.getInteger("upCount")))
                                    .onFailure(err -> logger.error("Scheduled scan failed for subnet '{}': {}", subnetName, err.getMessage()));

                        }

                    }

                })
                .onFailure(err -> logger.warn("Failed to check scheduled subnets: {}", err.getMessage()));

    }

}
