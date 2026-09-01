package com.motadata.ipam.service;

import com.motadata.ipam.database.DbQueries;

import com.motadata.ipam.database.DbUtil;

import io.vertx.core.Future;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonArray;

import io.vertx.core.json.JsonObject;

import io.vertx.mysqlclient.MySQLPool;

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

    private final MySQLPool mysqlPool;

    private final Vertx vertx;

    public AlertService(MySQLPool mysqlPool, Vertx vertx) {

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

                    publishAlert(subnetId, alertType, message, subnet)
                            .onSuccess(v -> {

                                vertx.eventBus().publish(ADDRESS_ALERT_STREAM, body);

                                msg.reply(new JsonObject().put("status", "PUBLISHED"));

                            })
                            .onFailure(err -> msg.fail(500, err.getMessage()));

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

}
