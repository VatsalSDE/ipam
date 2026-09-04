package com.motadata.ipam.alert;


import com.motadata.ipam.core.database.DbQueries;
import com.motadata.ipam.core.database.DbUtil;

import io.vertx.core.Future;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonArray;

import io.vertx.core.json.JsonObject;

import io.vertx.sqlclient.Pool;

import io.vertx.sqlclient.Row;

import io.vertx.sqlclient.Tuple;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

/**
 * Enterprise Alert Stream Service & In-Memory EventBus Publisher.
 */
public class AlertService {

    public static final String ADDRESS_ALERT_PUBLISH = "ipam.alert.publish";

    public static final String ADDRESS_ALERT_STREAM = "ipam.alert.stream";

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    private final Pool mysqlPool;

    private final Vertx vertx;

    public AlertService(Pool mysqlPool, Vertx vertx) {

        this.mysqlPool = mysqlPool;

        this.vertx = vertx;

        if (vertx != null && vertx.eventBus() != null) {

            vertx.eventBus().<JsonObject>consumer(ADDRESS_ALERT_PUBLISH, msg -> {

                JsonObject body = msg.body();

                if (body != null) {

                    Long subnetId = body.getLong("subnetId", 0L);

                    String alertType = body.getString("alertType", "GENERAL");

                    String message = body.getString("message", "");

                    String subnet = body.getString("subnet", "");

                    getAlertConfiguration().onSuccess(config -> {

                        boolean isAllowed = isAlertRuleEnabled(config, alertType, body);

                        if (!isAllowed) {

                            logger.info("Alert suppressed by user configuration: type={}", alertType);

                            msg.reply(new JsonObject().put("status", "SUPPRESSED_BY_CONFIG"));

                            return;

                        }

                        publishAlert(subnetId, alertType, message, subnet)
                                .onSuccess(v -> {

                                    vertx.eventBus().publish(ADDRESS_ALERT_STREAM, body);

                                    msg.reply(new JsonObject().put("status", "PUBLISHED"));

                                })
                                .onFailure(err -> msg.fail(500, err.getMessage()));

                    }).onFailure(err -> {

                        publishAlert(subnetId, alertType, message, subnet)
                                .onSuccess(v -> {

                                    vertx.eventBus().publish(ADDRESS_ALERT_STREAM, body);

                                    msg.reply(new JsonObject().put("status", "PUBLISHED"));

                                })
                                .onFailure(e -> msg.fail(500, e.getMessage()));

                    });

                }

            });

        }

    }

    /**
     * Lists alerts (all or active only) with pagination.
     */
    public Future<JsonObject> listAlerts(int limit, int offset, boolean activeOnly) {

        if (limit <= 0) {

            limit = 50;

        } else if (limit > 500) {

            limit = 500;

        }

        if (offset < 0) {

            offset = 0;

        }

        final int finalLimit = limit;

        final int finalOffset = offset;

        String countSql = activeOnly ? DbQueries.COUNT_ALERTS_ACTIVE : DbQueries.COUNT_ALERTS_ALL;

        String listSql = activeOnly ? DbQueries.LIST_ALERTS_ACTIVE : DbQueries.LIST_ALERTS_ALL;

        return mysqlPool.preparedQuery(countSql).execute()
                .compose(countRows -> {

                    long total = 0;

                    if (countRows.iterator().hasNext()) {

                        total = DbUtil.getLongOrDefault(countRows.iterator().next(), "total", 0L);

                    }

                    final long totalRecords = total;

                    return mysqlPool.preparedQuery(listSql)
                            .execute(Tuple.of(finalLimit, finalOffset))
                            .map(rows -> {

                                JsonArray alerts = new JsonArray();

                                for (Row row : rows) {

                                    alerts.add(mapAlertRow(row));

                                }

                                JsonObject result = new JsonObject();

                                result.put("total", totalRecords);

                                result.put("limit", finalLimit);

                                result.put("offset", finalOffset);

                                result.put("alerts", alerts);

                                return result;

                            });

                });

    }

    /**
     * Persists an alert into the alert_stream table and publishes to EventBus.
     */
    public Future<Void> publishAlert(Long subnetId, String alertType, String message, String subnet) {

        Tuple params = Tuple.of(subnetId, alertType, message, subnet);

        return mysqlPool.preparedQuery(DbQueries.INSERT_ALERT).execute(params).mapEmpty();

    }

    /**
     * Clears an active alert by ID (status = 0).
     */
    public Future<Void> clearAlert(Long alertId) {

        return mysqlPool.preparedQuery(DbQueries.CLEAR_ALERT_BY_ID).execute(Tuple.of(alertId)).mapEmpty();

    }

    /**
     * Clears all active alerts for a subnet.
     */
    public Future<Void> clearSubnetAlerts(Long subnetId) {

        return mysqlPool.preparedQuery(DbQueries.CLEAR_ALERTS_BY_SUBNET).execute(Tuple.of(subnetId)).mapEmpty();

    }

    /**
     * Deletes an alert record completely.
     */
    public Future<Void> deleteAlert(Long alertId) {

        return mysqlPool.preparedQuery(DbQueries.DELETE_ALERT_BY_ID).execute(Tuple.of(alertId)).mapEmpty();

    }

    /**
     * Retrieves current alert configuration with default fallbacks.
     */
    public Future<JsonObject> getAlertConfiguration() {

        return mysqlPool.preparedQuery(DbQueries.GET_ALERT_CONFIG).execute()
                .map(rows -> {

                    JsonObject config = new JsonObject();

                    // Apply standard system defaults
                    config.put("ipUtilization", "80");
                    config.put("ipUtilizationFlag", true);
                    config.put("ipUtilizationBelow", "20");
                    config.put("ipUtilizationBelowFlag", false);
                    config.put("macIpChangeFlag", false);
                    config.put("macIpChange", "");
                    config.put("rogueDetection", false);
                    config.put("ipStateChange", false);
                    config.put("reverseLookupFailed", false);
                    config.put("forwardLookupFailed", false);
                    config.put("forwardLookupMismatch", false);
                    config.put("ipReservationChange", false);
                    config.put("ipConflict", true);
                    config.put("newSubnetsDiscovered", true);

                    for (Row row : rows) {

                        String key = DbUtil.getString(row, "alertKey");

                        String val = DbUtil.getString(row, "alertValue");

                        if (key != null && val != null) {

                            if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) {

                                config.put(key, Boolean.parseBoolean(val));

                            } else {

                                config.put(key, val);

                            }

                        }

                    }

                    return config;

                })
                .recover(err -> {

                    logger.warn("Could not load alert configuration from database, using defaults: {}", err.getMessage());

                    JsonObject defaults = new JsonObject()
                            .put("ipUtilization", "80")
                            .put("ipUtilizationFlag", true)
                            .put("ipUtilizationBelow", "20")
                            .put("ipUtilizationBelowFlag", false)
                            .put("macIpChangeFlag", false)
                            .put("macIpChange", "")
                            .put("rogueDetection", false)
                            .put("ipStateChange", false)
                            .put("reverseLookupFailed", false)
                            .put("forwardLookupFailed", false)
                            .put("forwardLookupMismatch", false)
                            .put("ipReservationChange", false)
                            .put("ipConflict", true)
                            .put("newSubnetsDiscovered", true);

                    return Future.succeededFuture(defaults);

                });

    }

    /**
     * Updates alert configuration in the alert table.
     */
    public Future<JsonObject> updateAlertConfiguration(JsonObject payload) {

        if (payload == null || payload.isEmpty()) {

            return Future.failedFuture("Configuration payload is required");

        }

        java.util.List<Tuple> batch = new java.util.ArrayList<>();

        for (String key : payload.fieldNames()) {

            Object val = payload.getValue(key);

            String valStr = (val != null) ? String.valueOf(val) : "";

            batch.add(Tuple.of(key, valStr));

        }

        return mysqlPool.preparedQuery(DbQueries.UPSERT_ALERT_CONFIG).executeBatch(batch)
                .compose(v -> getAlertConfiguration());

    }

    private JsonObject mapAlertRow(Row row) {

        JsonObject alert = new JsonObject();

        alert.put("id", DbUtil.getLong(row, "id"));

        alert.put("subnetId", DbUtil.getLong(row, "subnetId"));

        alert.put("alertType", DbUtil.getString(row, "alertType"));

        alert.put("message", DbUtil.getString(row, "message"));

        alert.put("subnet", DbUtil.getString(row, "subnet"));

        alert.put("timestamp", DbUtil.getString(row, "timestamp"));

        alert.put("status", DbUtil.getBoolean(row, "status"));

        return alert;

    }

    private boolean isAlertRuleEnabled(JsonObject config, String alertType, JsonObject body) {

        if (config == null || alertType == null) {

            return true;

        }

        switch (alertType.toUpperCase()) {

            case "HIGH_UTILIZATION":
            case "IP_UTILIZATION": {

                Object flag = config.getValue("ipUtilizationFlag", true);

                boolean enabled = "true".equalsIgnoreCase(String.valueOf(flag)) || Boolean.TRUE.equals(flag);

                if (!enabled) return false;

                int threshold = 80;

                try {

                    threshold = Integer.parseInt(String.valueOf(config.getValue("ipUtilization", "80")));

                } catch (Exception ignored) {}

                int currentPct = body.getInteger("percentage", 100);

                return currentPct >= threshold;

            }

            case "LOW_UTILIZATION":
            case "IP_UTILIZATION_BELOW": {

                Object flag = config.getValue("ipUtilizationBelowFlag", false);

                boolean enabled = "true".equalsIgnoreCase(String.valueOf(flag)) || Boolean.TRUE.equals(flag);

                if (!enabled) return false;

                int threshold = 20;

                try {

                    threshold = Integer.parseInt(String.valueOf(config.getValue("ipUtilizationBelow", "20")));

                } catch (Exception ignored) {}

                int currentPct = body.getInteger("percentage", 0);

                return currentPct <= threshold;

            }

            case "ROGUE_DETECTION": {

                Object flag = config.getValue("rogueDetection", true);

                return "true".equalsIgnoreCase(String.valueOf(flag)) || Boolean.TRUE.equals(flag);

            }

            case "IP_CONFLICT": {

                Object flag = config.getValue("ipConflict", true);

                return "true".equalsIgnoreCase(String.valueOf(flag)) || Boolean.TRUE.equals(flag);

            }

            case "IP_STATE_CHANGE": {

                Object flag = config.getValue("ipStateChange", false);

                return "true".equalsIgnoreCase(String.valueOf(flag)) || Boolean.TRUE.equals(flag);

            }

            case "NEW_SUBNETS_DISCOVERED": {

                Object flag = config.getValue("newSubnetsDiscovered", true);

                return "true".equalsIgnoreCase(String.valueOf(flag)) || Boolean.TRUE.equals(flag);

            }

            default:
                return true;

        }

    }

}
