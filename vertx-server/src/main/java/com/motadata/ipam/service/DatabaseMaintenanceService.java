package com.motadata.ipam.service;

import com.motadata.ipam.database.DbQueries;

import com.motadata.ipam.database.DbUtil;

import io.vertx.core.CompositeFuture;

import io.vertx.core.Future;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonObject;

import io.vertx.mysqlclient.MySQLPool;

import io.vertx.sqlclient.Row;

import io.vertx.sqlclient.Tuple;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

/**
 * Enterprise Database Maintenance & Data Retention Engine.
 * Manages audit retention periods, old data pruning (events, alerts, change logs),
 * and automated daily retention cleanup schedulers.
 */
public class DatabaseMaintenanceService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMaintenanceService.class);

    private static final long DAILY_RETENTION_INTERVAL_MS = 86_400_000L; // 24 hours

    private final MySQLPool mysqlPool;

    private final Vertx vertx;

    private Long periodicTimerId;

    public DatabaseMaintenanceService(MySQLPool mysqlPool, Vertx vertx) {

        this.mysqlPool = mysqlPool;

        this.vertx = vertx;

        if (vertx != null) {

            startRetentionScheduler();

        }

    }

    public DatabaseMaintenanceService(MySQLPool mysqlPool) {

        this(mysqlPool, null);

    }

    /**
     * Retrieves current database maintenance and retention settings.
     */
    public Future<JsonObject> getSettings() {

        return mysqlPool.preparedQuery(DbQueries.GET_DATABASE_MAINTENANCE)
                .execute()
                .compose(rows -> {

                    if (!rows.iterator().hasNext()) {

                        JsonObject defaultSettings = new JsonObject();

                        defaultSettings.put("id", 1L);

                        defaultSettings.put("maintainedDays", 30);

                        defaultSettings.put("status", "enable");

                        defaultSettings.put("scheduleStatus", false);

                        defaultSettings.put("backupPath", "/backup");

                        return Future.succeededFuture(defaultSettings);

                    }

                    Row row = rows.iterator().next();

                    JsonObject settings = new JsonObject();

                    settings.put("id", DbUtil.getLong(row, "id"));

                    int maintainedDays = DbUtil.getIntOrDefault(row, "maintainedDays", 30);

                    settings.put("maintainedDays", maintainedDays);

                    String rawStatus = DbUtil.getString(row, "status");

                    String status = ("1".equals(rawStatus) || "enable".equalsIgnoreCase(rawStatus)) ? "enable" : "disable";

                    settings.put("status", status);

                    settings.put("backupPath", DbUtil.getString(row, "backupPath"));

                    settings.put("duration", DbUtil.getString(row, "duration"));

                    settings.put("scheduleHour", DbUtil.getIntOrDefault(row, "scheduleHour", 0));

                    settings.put("scheduleStatus", DbUtil.getBoolean(row, "scheduleStatus"));

                    return Future.succeededFuture(settings);

                });

    }

    /**
     * Updates database maintenance retention configuration.
     */
    public Future<JsonObject> updateSettings(JsonObject payload) {

        if (payload == null) {

            return Future.failedFuture("Payload is required");

        }

        int maintainedDays = payload.getInteger("maintainedDays", 30);

        if (maintainedDays <= 0) {

            maintainedDays = 30;

        }

        String rawStatus = payload.getString("status", "enable");

        String dbStatus = ("enable".equalsIgnoreCase(rawStatus) || "1".equals(rawStatus)) ? "1" : "0";

        boolean scheduleStatus = payload.getBoolean("scheduleStatus", false);

        int scheduleBit = scheduleStatus ? 1 : 0;

        final int finalDays = maintainedDays;

        final String finalStatus = "1".equals(dbStatus) ? "enable" : "disable";

        Tuple params = Tuple.of(finalDays, dbStatus, scheduleBit);

        return mysqlPool.preparedQuery(DbQueries.UPDATE_DATABASE_MAINTENANCE)
                .execute(params)
                .compose(res -> {

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "DATABASE_MAINTENANCE_UPDATED")
                                .put("eventContext", "Updated data retention policy to " + finalDays + " days (Status: " + finalStatus + ")")
                                .put("severity", 1));

                    }

                    return getSettings();

                });

    }

    /**
     * Executes manual or scheduled data retention pruning across events, change logs, and alerts.
     */
    public Future<JsonObject> purgeOldData(int days) {

        if (days <= 0) {

            days = 30;

        }

        final int finalDays = days;

        Future<Integer> purgeEvents = mysqlPool.preparedQuery(DbQueries.PURGE_OLD_EVENTS)
                .execute(Tuple.of(finalDays))
                .map(rows -> rows.rowCount());

        Future<Integer> purgeLogs = mysqlPool.preparedQuery(DbQueries.PURGE_OLD_IP_CHANGE_LOGS)
                .execute(Tuple.of(finalDays))
                .map(rows -> rows.rowCount());

        Future<Integer> purgeAlerts = mysqlPool.preparedQuery(DbQueries.PURGE_OLD_ALERTS)
                .execute(Tuple.of(finalDays))
                .map(rows -> rows.rowCount());

        return CompositeFuture.all(purgeEvents, purgeLogs, purgeAlerts)
                .map(comp -> {

                    int eventsDeleted = comp.resultAt(0);

                    int logsDeleted = comp.resultAt(1);

                    int alertsDeleted = comp.resultAt(2);

                    int totalDeleted = eventsDeleted + logsDeleted + alertsDeleted;

                    logger.info("Data retention cleanup completed: purged {} events, {} change logs, {} alerts older than {} days",
                            eventsDeleted, logsDeleted, alertsDeleted, finalDays);

                    JsonObject result = new JsonObject();

                    result.put("purged", true);

                    result.put("maintainedDays", finalDays);

                    result.put("eventsPurged", eventsDeleted);

                    result.put("logsPurged", logsDeleted);

                    result.put("alertsPurged", alertsDeleted);

                    result.put("totalPurged", totalDeleted);

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "DATA_RETENTION_PURGE")
                                .put("eventContext", "Data retention cleanup purged " + totalDeleted + " records older than " + finalDays + " days")
                                .put("severity", 1));

                    }

                    return result;

                });

    }

    /**
     * Starts background periodic retention scheduler (runs every 24 hours).
     */
    public void startRetentionScheduler() {

        if (vertx == null) {

            return;

        }

        logger.info("Starting Database Retention Scheduler (interval: {} ms)", DAILY_RETENTION_INTERVAL_MS);

        this.periodicTimerId = vertx.setPeriodic(DAILY_RETENTION_INTERVAL_MS, id -> {

            getSettings().onSuccess(settings -> {

                String status = settings.getString("status", "disable");

                int maintainedDays = settings.getInteger("maintainedDays", 30);

                if ("enable".equalsIgnoreCase(status) && maintainedDays > 0) {

                    logger.info("Triggering scheduled daily data retention cleanup (retention: {} days)...", maintainedDays);

                    purgeOldData(maintainedDays)
                            .onFailure(err -> logger.error("Scheduled retention cleanup failed: {}", err.getMessage(), err));

                }

            });

        });

    }

    public void stopRetentionScheduler() {

        if (vertx != null && periodicTimerId != null) {

            vertx.cancelTimer(periodicTimerId);

            periodicTimerId = null;

            logger.info("Database Retention Scheduler stopped");

        }

    }

}
