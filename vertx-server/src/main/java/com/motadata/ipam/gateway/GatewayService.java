package com.motadata.ipam.gateway;


import com.motadata.ipam.core.database.DbQueries;
import com.motadata.ipam.core.database.DbUtil;
import com.motadata.ipam.event.EventService;

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
 * Concise, high-performance service for Gateway and Discovered Subnet persistence.
 */
public class GatewayService {

    private static final Logger logger = LoggerFactory.getLogger(GatewayService.class);

    private final Pool mysqlPool;

    private final Vertx vertx;

    public GatewayService(Pool mysqlPool, Vertx vertx) {

        this.mysqlPool = mysqlPool;

        this.vertx = vertx;

    }

    public GatewayService(Pool mysqlPool) {

        this(mysqlPool, null);

    }

    public Future<JsonArray> listGateways() {

        return mysqlPool.preparedQuery(DbQueries.LIST_GATEWAYS).execute()
                .map(rows -> {

                    JsonArray list = new JsonArray();

                    for (Row r : rows) {

                        list.add(new JsonObject()
                                .put("id", DbUtil.getLong(r, "id"))
                                .put("name", DbUtil.getString(r, "name"))
                                .put("gateway", DbUtil.getString(r, "gateway"))
                                .put("version", DbUtil.getString(r, "version"))
                                .put("community", DbUtil.getString(r, "community"))
                                .put("securityLevel", DbUtil.getString(r, "securityLevel"))
                                .put("status", DbUtil.getString(r, "status"))
                                .put("previousScan", DbUtil.getString(r, "previousScan").isEmpty() ? "Never" : DbUtil.getString(r, "previousScan")));

                    }

                    return list;

                });

    }

    public Future<JsonObject> getGatewayById(long id) {

        return mysqlPool.preparedQuery(DbQueries.FIND_GATEWAY_BY_ID).execute(Tuple.of(id))
                .map(rows -> {

                    for (Row r : rows) {

                        return new JsonObject()
                                .put("id", DbUtil.getLong(r, "id"))
                                .put("name", DbUtil.getString(r, "name"))
                                .put("gateway", DbUtil.getString(r, "gateway"))
                                .put("version", DbUtil.getString(r, "version"))
                                .put("community", DbUtil.getString(r, "community"))
                                .put("securityLevel", DbUtil.getString(r, "securityLevel"))
                                .put("authenticationProtocol", DbUtil.getString(r, "authenticationProtocol"))
                                .put("authenticationPassword", DbUtil.getString(r, "authenticationPassword"))
                                .put("privacyProtocol", DbUtil.getString(r, "privacyProtocol"))
                                .put("privatePassword", DbUtil.getString(r, "privatePassword"))
                                .put("securityUserName", DbUtil.getString(r, "securityUserName"))
                                .put("status", DbUtil.getString(r, "status"));

                    }

                    return null;

                });

    }

    public Future<Long> addGateway(JsonObject body) {

        Tuple params = Tuple.of(
                body.getString("name"),
                body.getString("gateway"),
                body.getString("version", "v2c"),
                body.getString("community", "public"),
                body.getString("securityLevel"),
                body.getString("authenticationProtocol"),
                body.getString("authenticationPassword"),
                body.getString("privacyProtocol"),
                body.getString("privatePassword"),
                body.getString("securityUserName"),
                body.getString("status", "UP")
        );

        return mysqlPool.preparedQuery(DbQueries.INSERT_GATEWAY).execute(params)
                .map(res -> {

                    Long insertedId = res.property(io.vertx.mysqlclient.MySQLClient.LAST_INSERTED_ID);

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "GATEWAY_CREATED")
                                .put("eventContext", "Added SNMP Gateway '" + body.getString("name") + "' (" + body.getString("gateway") + ")")
                                .put("severity", 1));

                    }

                    return insertedId;

                });

    }

    public Future<Void> deleteGateway(long id) {

        return mysqlPool.preparedQuery(DbQueries.DELETE_GATEWAY).execute(Tuple.of(id))
                .map(res -> {

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "GATEWAY_DELETED")
                                .put("eventContext", "Deleted Gateway ID " + id)
                                .put("severity", 2));

                    }

                    return null;

                });

    }

    public Future<Void> updateScanStatus(long id, String status) {

        return mysqlPool.preparedQuery(DbQueries.UPDATE_GATEWAY_SCAN_TIME).execute(Tuple.of(status, id)).mapEmpty();

    }

    public Future<JsonArray> listDiscoveredSubnets() {

        return mysqlPool.preparedQuery(DbQueries.LIST_DISCOVERED_SUBNETS).execute()
                .map(rows -> {

                    JsonArray list = new JsonArray();

                    for (Row r : rows) {

                        list.add(new JsonObject()
                                .put("id", DbUtil.getLong(r, "id"))
                                .put("gateway", DbUtil.getString(r, "gateway"))
                                .put("gatewayId", DbUtil.getLong(r, "gatewayId"))
                                .put("subnet", DbUtil.getString(r, "subnet"))
                                .put("subnetMask", DbUtil.getString(r, "subnetMask")));

                    }

                    return list;

                });

    }

    public Future<JsonObject> getDiscoveredSubnetById(long id) {

        return mysqlPool.preparedQuery(DbQueries.FIND_DISCOVERED_SUBNET_BY_ID).execute(Tuple.of(id))
                .map(rows -> {

                    for (Row r : rows) {

                        return new JsonObject()
                                .put("id", DbUtil.getLong(r, "id"))
                                .put("gateway", DbUtil.getString(r, "gateway"))
                                .put("gatewayId", DbUtil.getLong(r, "gatewayId"))
                                .put("subnet", DbUtil.getString(r, "subnet"))
                                .put("subnetMask", DbUtil.getString(r, "subnetMask"));

                    }

                    return null;

                });

    }

    public Future<Void> saveDiscoveredSubnet(String gateway, Long gatewayId, String subnet, String subnetMask) {

        return mysqlPool.preparedQuery(DbQueries.INSERT_DISCOVERED_SUBNET)
                .execute(Tuple.of(gateway, gatewayId, subnet, subnetMask))
                .map(res -> {

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "DISCOVERY_SUBNET_FOUND")
                                .put("eventContext", "Discovered new subnet " + subnet + " (" + subnetMask + ") from Gateway '" + gateway + "'")
                                .put("severity", 1));

                    }

                    return null;

                });

    }

    public Future<Void> deleteDiscoveredSubnet(long id) {

        return mysqlPool.preparedQuery(DbQueries.DELETE_DISCOVERED_SUBNET).execute(Tuple.of(id)).mapEmpty();

    }
}
