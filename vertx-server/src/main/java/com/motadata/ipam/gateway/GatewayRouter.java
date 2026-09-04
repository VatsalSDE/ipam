package com.motadata.ipam.gateway;


import com.motadata.ipam.scanner.GoPluginBridge;

import com.motadata.ipam.security.RbacAuthHandler;

import io.vertx.core.Vertx;

import io.vertx.ext.web.Router;

import io.vertx.sqlclient.Pool;

/**
 * Clean router registering Gateway and Discovered Subnet endpoints with RBAC protection.
 */
public class GatewayRouter {

    public static void register(Router router, Pool mysqlPool, GoPluginBridge goPluginBridge, Vertx vertx, RbacAuthHandler rbacAuthHandler) {

        GatewayService gatewayService = new GatewayService(mysqlPool, vertx);

        GatewayHandler handler = new GatewayHandler(gatewayService, goPluginBridge);

        // Gateways CRUD & Scan
        router.get("/api/gateway")
                .handler(rbacAuthHandler.requirePermission("PERM_DISCOVERY_READ"))
                .handler(handler::listGateways);

        router.get("/api/gateway/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_DISCOVERY_READ"))
                .handler(handler::getGateway);

        router.post("/api/gateway")
                .handler(rbacAuthHandler.requirePermission("PERM_DISCOVERY_WRITE"))
                .handler(handler::createGateway);

        router.delete("/api/gateway/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_DISCOVERY_WRITE"))
                .handler(handler::deleteGateway);

        router.post("/api/gateway/:id/scan")
                .handler(rbacAuthHandler.requirePermission("PERM_DISCOVERY_WRITE"))
                .handler(handler::scanGateway);

        // Discovered Subnets CRUD
        router.get("/api/discovered-subnet")
                .handler(rbacAuthHandler.requirePermission("PERM_DISCOVERY_READ"))
                .handler(handler::listDiscoveredSubnets);

        router.get("/api/discovered-subnet/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_DISCOVERY_READ"))
                .handler(handler::getDiscoveredSubnet);

        router.delete("/api/discovered-subnet/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_DISCOVERY_WRITE"))
                .handler(handler::deleteDiscoveredSubnet);

    }

}
