package com.motadata.ipam.service;

import com.motadata.ipam.database.DbQueries;

import com.motadata.ipam.database.DbUtil;

import io.vertx.core.Future;

import io.vertx.core.json.JsonArray;

import io.vertx.core.json.JsonObject;

import io.vertx.mysqlclient.MySQLPool;

import io.vertx.sqlclient.Row;

import io.vertx.sqlclient.Tuple;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import java.util.List;

/**
 * Dedicated Subnet IP Address Query & Pagination Service.
 * Isolates child IP address search, filtering, and pagination from parent Subnet CRUD.
 */
public class SubnetIpService {

    private static final Logger logger = LoggerFactory.getLogger(SubnetIpService.class);

    private final MySQLPool mysqlPool;

    public SubnetIpService(MySQLPool mysqlPool) {

        this.mysqlPool = mysqlPool;

    }

    /**
     * Fetches paginated IP address rows for a specific subnet with optional status and search filtering.
     * Enforces a strict max limit ceiling of 500 to prevent EventLoop CPU lag.
     */
    public Future<JsonObject> getSubnetIps(Long subnetId, int limit, int offset, String statusFilter, String search) {

        if (subnetId == null || subnetId <= 0) {

            return Future.failedFuture("Invalid subnet ID");

        }

        if (limit <= 0) {

            limit = 50;

        } else if (limit > 500) {

            limit = 500;

        }

        if (offset < 0) {

            offset = 0;

        }

        StringBuilder countSql = new StringBuilder(DbQueries.COUNT_SUBNET_IPS_BASE);

        StringBuilder selectSql = new StringBuilder(DbQueries.SELECT_SUBNET_IPS_BASE);

        List<Object> params = new ArrayList<>();

        params.add(subnetId);

        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {

            countSql.append(" AND status = ?");

            selectSql.append(" AND status = ?");

            params.add(statusFilter.toUpperCase());

        }

        if (search != null && !search.isBlank()) {

            String searchPattern = "%" + search.trim() + "%";

            countSql.append(" AND (ip_address LIKE ? OR mac_address LIKE ? OR host_name LIKE ?)");

            selectSql.append(" AND (ip_address LIKE ? OR mac_address LIKE ? OR host_name LIKE ?)");

            params.add(searchPattern);

            params.add(searchPattern);

            params.add(searchPattern);

        }

        selectSql.append(" ORDER BY id ASC LIMIT ? OFFSET ?");

        List<Object> selectParams = new ArrayList<>(params);

        selectParams.add(limit);

        selectParams.add(offset);

        final int finalLimit = limit;

        final int finalOffset = offset;

        return mysqlPool.preparedQuery(countSql.toString()).execute(Tuple.from(params))
                .compose(countRows -> {

                    long total = 0;

                    if (countRows.iterator().hasNext()) {

                        total = DbUtil.getLongOrDefault(countRows.iterator().next(), "total", 0L);

                    }

                    final long totalRecords = total;

                    return mysqlPool.preparedQuery(selectSql.toString()).execute(Tuple.from(selectParams))
                            .map(rows -> {

                                JsonArray ips = new JsonArray();

                                for (Row row : rows) {

                                    ips.add(mapIpRow(row));

                                }

                                JsonObject result = new JsonObject();

                                result.put("total", totalRecords);

                                result.put("limit", finalLimit);

                                result.put("offset", finalOffset);

                                result.put("ips", ips);

                                return result;

                            });

                });

    }

    /**
     * Maps a database Row from subnet_ip_details into a clean JsonObject.
     */
    private JsonObject mapIpRow(Row row) {

        JsonObject ipObj = new JsonObject();

        ipObj.put("id", DbUtil.getLong(row, "id"));

        ipObj.put("ipAddress", DbUtil.getString(row, "ip_address"));

        ipObj.put("macAddress", DbUtil.getString(row, "mac_address"));

        ipObj.put("status", DbUtil.getString(row, "status"));

        ipObj.put("deviceType", DbUtil.getString(row, "device_type"));

        ipObj.put("hostName", DbUtil.getString(row, "host_name"));

        ipObj.put("authenticity", DbUtil.getString(row, "authenticity"));

        return ipObj;

    }

}
