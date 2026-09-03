package com.motadata.ipam.event;


import com.motadata.ipam.core.database.DbQueries;
import com.motadata.ipam.core.database.DbUtil;

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
 * High-performance, non-blocking Event & Audit Notification Service.
 */
public class EventService {

    public static final String ADDRESS_EVENT_LOG = "ipam.event.log";

    private static final Logger logger = LoggerFactory.getLogger(EventService.class);

    private final MySQLPool mysqlPool;

    private final Vertx vertx;

    public EventService(MySQLPool mysqlPool, Vertx vertx) {

        this.mysqlPool = mysqlPool;

        this.vertx = vertx;

        if (vertx != null && vertx.eventBus() != null) {

            vertx.eventBus().<JsonObject>consumer(ADDRESS_EVENT_LOG, msg -> {

                JsonObject body = msg.body();

                if (body != null) {

                    String eventType = body.getString("eventType", "SYSTEM_EVENT");

                    String eventContext = body.getString("eventContext", "");

                    int severity = body.getInteger("severity", 3);

                    Long userId = body.getLong("userId", null);

                    logEvent(eventType, eventContext, severity, userId)
                            .onFailure(err -> logger.debug("Could not record async event: {}", err.getMessage()));

                }

            });

        }

    }

    public EventService(MySQLPool mysqlPool) {

        this(mysqlPool, null);

    }

    /**
     * Lists paginated event notifications with optional timeline filtering.
     */
    public Future<JsonObject> listEvents(int limit, int offset, Integer timeline) {

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

        String timelineWhere = buildTimelineWhere(timeline);

        String countSql = "SELECT COUNT(*) as total FROM event e " + timelineWhere;

        String listSql = "SELECT e.id, e.timestamp, e.event_type as eventType, e.event_context as eventContext, e.severity, u.user_name as userName " +
                "FROM event e LEFT JOIN user u ON e.done_by_id = u.id " + timelineWhere + " ORDER BY e.id DESC LIMIT ? OFFSET ?";

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

                                JsonArray events = new JsonArray();

                                for (Row row : rows) {

                                    events.add(mapEventRow(row));

                                }

                                JsonObject result = new JsonObject();

                                result.put("total", totalRecords);

                                result.put("limit", finalLimit);

                                result.put("offset", finalOffset);

                                result.put("events", events);

                                return result;

                            });

                });

    }

    /**
     * Lists top 25 recent events for dashboard view.
     */
    public Future<JsonArray> listTopEvents() {

        return mysqlPool.preparedQuery(DbQueries.LIST_TOP_EVENTS).execute()
                .map(rows -> {

                    JsonArray events = new JsonArray();

                    for (Row row : rows) {

                        events.add(mapEventRow(row));

                    }

                    return events;

                });

    }

    /**
     * Aggregates 12-month event summary for Dashboard sparkline.
     */
    public Future<JsonArray> get12MonthEventSummary() {

        java.time.YearMonth current = java.time.YearMonth.now();
        java.time.format.DateTimeFormatter ymFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");
        java.time.format.DateTimeFormatter displayFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM");

        java.util.Map<String, JsonObject> monthMap = new java.util.LinkedHashMap<>();

        for (int i = 11; i >= 0; i--) {

            java.time.YearMonth ym = current.minusMonths(i);

            String ymKey = ym.format(ymFormatter);

            String displayName = ym.format(displayFormatter);

            monthMap.put(ymKey, new JsonObject()
                    .put("name", displayName)
                    .put("monthKey", ymKey)
                    .put("count", 0)
                    .put("severity", 3)
                    .put("criticalCount", 0)
                    .put("warningCount", 0)
                    .put("infoCount", 0)
                    .put("color", "#00b3ee"));

        }

        return mysqlPool.preparedQuery(DbQueries.GET_12_MONTH_EVENT_SUMMARY).execute()
                .map(rows -> {

                    for (Row r : rows) {

                        String key = DbUtil.getString(r, "monthName");

                        if (monthMap.containsKey(key)) {

                            int sev = DbUtil.getIntOrDefault(r, "worstSeverity", 3);

                            String color = (sev == 1) ? "#FF0000" : (sev == 2 ? "#FFA31A" : "#00b3ee");

                            JsonObject item = monthMap.get(key);

                            item.put("count", DbUtil.getLong(r, "totalEvents"))
                                    .put("severity", sev)
                                    .put("criticalCount", DbUtil.getLong(r, "criticalCount"))
                                    .put("warningCount", DbUtil.getLong(r, "warningCount"))
                                    .put("infoCount", DbUtil.getLong(r, "infoCount"))
                                    .put("color", color);

                        }

                    }

                    JsonArray result = new JsonArray();

                    for (JsonObject val : monthMap.values()) {

                        result.add(val);

                    }

                    return result;

                });

    }

    /**
     * Logs an audit event into the database asynchronously with user ID.
     */
    public Future<Void> logEvent(String eventType, String eventContext, int severity, Long userId) {

        Tuple params = Tuple.of(eventType, eventContext, severity, userId);

        return mysqlPool.preparedQuery(DbQueries.INSERT_EVENT).execute(params).mapEmpty();

    }

    private String buildTimelineWhere(Integer timeline) {

        if (timeline == null || timeline == 10) {

            return "";

        }

        switch (timeline) {

            case 1:

                return "WHERE DATE(e.timestamp) = CURDATE()";

            case 2:

                return "WHERE DATE(e.timestamp) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)";

            case 3:

                return "WHERE WEEK(e.timestamp) = WEEK(CURDATE()) AND YEAR(e.timestamp) = YEAR(CURDATE())";

            case 4:

                return "WHERE MONTH(e.timestamp) = MONTH(CURDATE()) AND YEAR(e.timestamp) = YEAR(CURDATE())";

            case 5:

                return "WHERE QUARTER(e.timestamp) = QUARTER(CURDATE()) AND YEAR(e.timestamp) = YEAR(CURDATE())";

            case 6:

                return "WHERE QUARTER(e.timestamp) = QUARTER(CURDATE()) - 1 AND YEAR(e.timestamp) = YEAR(CURDATE())";

            case 7:

                return "WHERE e.timestamp >= DATE_SUB(NOW(), INTERVAL 6 MONTH)";

            case 8:

                return "WHERE YEAR(e.timestamp) = YEAR(CURDATE())";

            case 9:

                return "WHERE YEAR(e.timestamp) = YEAR(CURDATE()) - 1";

            case 11:

                return "WHERE WEEK(e.timestamp) = WEEK(CURDATE()) - 1 AND YEAR(e.timestamp) = YEAR(CURDATE())";

            case 12:

                return "WHERE MONTH(e.timestamp) = MONTH(CURDATE()) - 1 AND YEAR(e.timestamp) = YEAR(CURDATE())";

            default:

                return "";

        }

    }

    private JsonObject mapEventRow(Row row) {

        JsonObject event = new JsonObject();

        event.put("id", DbUtil.getLong(row, "id"));

        event.put("timestamp", DbUtil.getString(row, "timestamp"));

        event.put("eventType", DbUtil.getString(row, "eventType"));

        event.put("eventContext", DbUtil.getString(row, "eventContext"));

        event.put("severity", DbUtil.getIntOrDefault(row, "severity", 1));

        String userName = DbUtil.getString(row, "userName");

        if (userName == null || userName.isBlank()) {

            userName = "System";

        }

        event.put("userName", userName);

        JsonObject doneBy = new JsonObject();

        doneBy.put("userName", userName);

        event.put("doneBy", doneBy);

        return event;

    }

}

