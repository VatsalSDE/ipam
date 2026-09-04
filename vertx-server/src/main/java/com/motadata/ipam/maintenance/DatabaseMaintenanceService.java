package com.motadata.ipam.maintenance;


import com.motadata.ipam.core.database.DbQueries;

import com.motadata.ipam.core.database.DbUtil;

import com.motadata.ipam.event.EventService;

import io.vertx.core.Future;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonObject;

import io.vertx.sqlclient.Pool;

import io.vertx.sqlclient.Row;

import io.vertx.sqlclient.Tuple;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.io.File;

import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;

import java.util.concurrent.TimeUnit;

/**
 * Enterprise Database Maintenance & Data Retention Engine.
 * Manages audit retention periods, old data pruning (events, alerts, change logs),
 * and automated daily retention cleanup schedulers.
 */
public class DatabaseMaintenanceService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMaintenanceService.class);

    private static final long DAILY_RETENTION_INTERVAL_MS = 86_400_000L; // 24 hours

    private final Pool mysqlPool;

    private final Vertx vertx;

    private Long periodicTimerId;

    public DatabaseMaintenanceService(Pool mysqlPool, Vertx vertx) {

        this.mysqlPool = mysqlPool;

        this.vertx = vertx;

        if (vertx != null) {

            startRetentionScheduler();

        }

    }

    public DatabaseMaintenanceService(Pool mysqlPool) {

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

        Integer maintainedDays = null;

        if (payload.containsKey("maintainedDays")) {

            Object md = payload.getValue("maintainedDays");

            if (md instanceof Number) {

                maintainedDays = ((Number) md).intValue();

            } else if (md instanceof String && !((String) md).isBlank()) {

                try {

                    maintainedDays = Integer.parseInt(((String) md).trim());

                } catch (Exception ignored) {}

            }

            if (maintainedDays != null && maintainedDays <= 0) {

                maintainedDays = 30;

            }

        }

        String dbStatus = null;

        if (payload.containsKey("status")) {

            Object st = payload.getValue("status");

            if (st != null) {

                String rawStatus = st.toString();

                dbStatus = ("enable".equalsIgnoreCase(rawStatus) || "1".equals(rawStatus)) ? "1" : "0";

            }

        }

        Integer scheduleBit = null;

        if (payload.containsKey("scheduleStatus")) {

            Object ss = payload.getValue("scheduleStatus");

            if (ss instanceof Boolean) {

                scheduleBit = ((Boolean) ss) ? 1 : 0;

            } else if (ss instanceof Number) {

                scheduleBit = (((Number) ss).intValue() == 1) ? 1 : 0;

            } else if (ss instanceof String) {

                boolean b = "true".equalsIgnoreCase((String) ss) || "1".equals(ss) || "enable".equalsIgnoreCase((String) ss);

                scheduleBit = b ? 1 : 0;

            }

        }

        String backupPath = payload.getString("backupPath");

        String duration = payload.getString("duration");

        Integer scheduleHour = null;

        if (payload.containsKey("scheduleHour")) {

            Object sh = payload.getValue("scheduleHour");

            if (sh instanceof Number) {

                scheduleHour = ((Number) sh).intValue();

            } else if (sh instanceof String && !((String) sh).isBlank()) {

                try {

                    scheduleHour = Integer.parseInt(((String) sh).trim());

                } catch (Exception ignored) {}

            }

        }

        final Integer finalDays = maintainedDays;

        Tuple params = Tuple.of(maintainedDays, dbStatus, scheduleBit, backupPath, duration, scheduleHour);

        return mysqlPool.preparedQuery(DbQueries.UPDATE_DATABASE_MAINTENANCE)
                .execute(params)
                .compose(res -> {

                    if (vertx != null && vertx.eventBus() != null) {

                        String context = "Updated database maintenance settings";

                        if (finalDays != null) {

                            context += " (Retention: " + finalDays + " days)";

                        }

                        if (backupPath != null) {

                            context += " (Backup Path: " + backupPath + ")";

                        }

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "DATABASE_MAINTENANCE_UPDATED")
                                .put("eventContext", context)
                                .put("severity", 1));

                    }

                    return getSettings();

                });

    }

    /**
     * Executes database backup export to backupPath using non-blocking worker execution.
     */
    public Future<JsonObject> runDatabaseBackup(String customPath) {

        return getSettings().compose(settings -> {

            String configuredPath = (customPath != null && !customPath.isBlank())
                    ? customPath
                    : settings.getString("backupPath", "/home/vatsal-rathi/Downloads/IPAM/backup");

            if (vertx == null) {

                return Future.failedFuture("Vert.x instance is required to execute background database backup");

            }

            return vertx.executeBlocking(() -> {

                try {

                    File dir = new File(configuredPath);

                    if (!dir.exists()) {

                        dir.mkdirs();

                    }

                    String timestamp = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss").format(LocalDateTime.now());

                    File backupFile = new File(dir, "DatabaseBackup_" + timestamp + ".sql");

                    ProcessBuilder pb = new ProcessBuilder(
                            "mysqldump",
                            "-u", "root",
                            "-pMind@123",
                            "ipam",
                            "--result-file=" + backupFile.getAbsolutePath()
                    );

                    pb.redirectErrorStream(true);

                    Process process = pb.start();

                    boolean completed = process.waitFor(60, TimeUnit.SECONDS);

                    if (!completed) {

                        process.destroyForcibly();

                        throw new RuntimeException("Database backup process timed out after 60 seconds");

                    }

                    long sizeBytes = backupFile.exists() ? backupFile.length() : 0L;

                    JsonObject result = new JsonObject();

                    result.put("success", true);

                    result.put("backupFile", backupFile.getAbsolutePath());

                    result.put("sizeBytes", sizeBytes);

                    result.put("timestamp", timestamp);

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "DATABASE_BACKUP_COMPLETED")
                                .put("eventContext", "Database backup saved to " + backupFile.getAbsolutePath() + " (" + sizeBytes + " bytes)")
                                .put("severity", 1));

                    }

                    logger.info("Database backup completed successfully at: {}", backupFile.getAbsolutePath());

                    return result;

                } catch (Exception e) {

                    logger.error("Database backup execution failed: {}", e.getMessage(), e);

                    throw new RuntimeException("Database backup failed: " + e.getMessage(), e);

                }

            });

        });

    }

    /**
     * Executes manual or scheduled data retention pruning across events, change logs, and alerts.
     * Takes an automated safety snapshot before deletion.
     */
    public Future<JsonObject> purgeOldData(int days) {

        if (days <= 0) {

            days = 30;

        }

        final int finalDays = days;

        return runDatabaseBackup(null)
                .recover(backupErr -> {

                    logger.warn("Pre-purge safety backup was skipped or failed: {}", backupErr.getMessage());

                    return Future.succeededFuture(new JsonObject().put("backupSkipped", true));

                })
                .compose(backupResult -> {

                    Future<Integer> purgeEvents = mysqlPool.preparedQuery(DbQueries.PURGE_OLD_EVENTS)
                            .execute(Tuple.of(finalDays))
                            .map(rows -> rows.rowCount());

                    Future<Integer> purgeLogs = mysqlPool.preparedQuery(DbQueries.PURGE_OLD_IP_CHANGE_LOGS)
                            .execute(Tuple.of(finalDays))
                            .map(rows -> rows.rowCount());

                    Future<Integer> purgeAlerts = mysqlPool.preparedQuery(DbQueries.PURGE_OLD_ALERTS)
                            .execute(Tuple.of(finalDays))
                            .map(rows -> rows.rowCount());

                    return Future.all(purgeEvents, purgeLogs, purgeAlerts)
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

                                if (backupResult.containsKey("backupFile")) {

                                    result.put("safetyBackupFile", backupResult.getString("backupFile"));

                                }

                                if (vertx != null && vertx.eventBus() != null) {

                                    vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                            .put("eventType", "DATA_RETENTION_PURGE")
                                            .put("eventContext", "Data retention cleanup purged " + totalDeleted + " records older than " + finalDays + " days")
                                            .put("severity", 1));

                                }

                                return result;

                            });

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
