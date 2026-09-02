package com.motadata.ipam.rogue;


import com.motadata.ipam.core.database.DbQueries;
import com.motadata.ipam.core.database.DbUtil;
import com.motadata.ipam.event.EventService;

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
 * Rogue Detection & Trusted Device Whitelist Service.
 */
public class RogueDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(RogueDetectionService.class);

    private final MySQLPool mysqlPool;

    private final Vertx vertx;

    public RogueDetectionService(MySQLPool mysqlPool, Vertx vertx) {

        this.mysqlPool = mysqlPool;

        this.vertx = vertx;

    }

    public RogueDetectionService(MySQLPool mysqlPool) {

        this(mysqlPool, null);

    }

    /**
     * Lists paginated discovered and rogue devices.
     */
    public Future<JsonObject> listRogueDevices(int limit, int offset) {

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

        return mysqlPool.preparedQuery(DbQueries.COUNT_ROGUE_DEVICES).execute()
                .compose(countRows -> {

                    long total = 0;

                    if (countRows.iterator().hasNext()) {

                        total = DbUtil.getLongOrDefault(countRows.iterator().next(), "total", 0L);

                    }

                    final long totalRecords = total;

                    return mysqlPool.preparedQuery(DbQueries.LIST_ROGUE_DEVICES)
                            .execute(Tuple.of(finalLimit, finalOffset))
                            .map(rows -> {

                                JsonArray devices = new JsonArray();

                                for (Row row : rows) {

                                    devices.add(mapRogueRow(row));

                                }

                                JsonObject result = new JsonObject();

                                result.put("total", totalRecords);

                                result.put("limit", finalLimit);

                                result.put("offset", finalOffset);

                                result.put("devices", devices);

                                return result;

                            });

                });

    }

    /**
     * Records a new discovered/rogue device into rogue_detection_details.
     */
    public Future<Void> addRogueDevice(String macAddress, String ipAddress, String nicType, String authenticity) {

        Tuple params = Tuple.of(macAddress, ipAddress, nicType, authenticity != null ? authenticity : "DISCOVERED");

        return mysqlPool.preparedQuery(DbQueries.INSERT_ROGUE_DEVICE).execute(params)
                .onSuccess(res -> {

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "ROGUE_MAC_DETECTED")
                                .put("eventContext", "Detected device MAC '" + macAddress + "' on IP " + ipAddress + " (State: " + (authenticity != null ? authenticity : "DISCOVERED") + ")")
                                .put("severity", 2));

                    }

                })
                .mapEmpty();

    }

    /**
     * Updates device authenticity state (TRUSTED, ROGUE, DISCOVERED).
     */
    public Future<Void> updateAuthenticity(long id, String authenticity) {

        return mysqlPool.preparedQuery(DbQueries.UPDATE_ROGUE_AUTHENTICITY)
                .execute(Tuple.of(authenticity.toUpperCase(), id))
                .onSuccess(res -> {

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "ROGUE_STATE_CHANGED")
                                .put("eventContext", "Updated Device ID #" + id + " authenticity to '" + authenticity.toUpperCase() + "'")
                                .put("severity", 1));

                    }

                })
                .mapEmpty();

    }

    /**
     * Deletes a rogue detection record by ID.
     */
    public Future<Void> deleteRogueDevice(long id) {

        return mysqlPool.preparedQuery(DbQueries.DELETE_ROGUE_DEVICE).execute(Tuple.of(id))
                .onSuccess(res -> {

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "ROGUE_DEVICE_DELETED")
                                .put("eventContext", "Deleted Device ID #" + id + " from Rogue tracking")
                                .put("severity", 2));

                    }

                })
                .mapEmpty();

    }

    private JsonObject mapRogueRow(Row row) {

        JsonObject device = new JsonObject();

        device.put("id", DbUtil.getLong(row, "id"));

        device.put("macAddress", DbUtil.getString(row, "macAddress"));

        device.put("ipAddress", DbUtil.getString(row, "ipAddress"));

        device.put("discoveredAt", DbUtil.getString(row, "discoveredAt"));

        device.put("nicType", DbUtil.getString(row, "nicType"));

        device.put("authenticity", DbUtil.getString(row, "authenticity"));

        return device;

    }

}

