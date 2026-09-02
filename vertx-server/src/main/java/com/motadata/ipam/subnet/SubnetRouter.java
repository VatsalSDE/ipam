package com.motadata.ipam.subnet;


import com.motadata.ipam.scanner.GoPluginBridge;
import com.motadata.ipam.scanner.ScannerHandler;
import com.motadata.ipam.security.RbacAuthHandler;

import io.vertx.core.Vertx;

import io.vertx.ext.web.Router;

import io.vertx.mysqlclient.MySQLPool;

/**
 * Modular Subnet Router.
 * Maps REST endpoints for Subnet CRUD, IP address queries, instant overlap validation,
 * and live ICMP network scanning with granular RBAC permissions.
 */
public class SubnetRouter {

    public static void register(Router router, MySQLPool mysqlPool, GoPluginBridge goPluginBridge, Vertx vertx, RbacAuthHandler rbacAuthHandler) {

        SubnetService subnetService = new SubnetService(mysqlPool, vertx);

        SubnetHandler subnetHandler = new SubnetHandler(subnetService);

        ScannerHandler scannerHandler = new ScannerHandler(vertx);

        // 1. List Subnets (Paginated, Searchable)
        router.get("/api/subnet")
                .handler(rbacAuthHandler.requirePermission("PERM_SUBNET_VIEW"))
                .handler(subnetHandler::list);

        // 2. Instant Overlap & CIDR Check (For Frontend Modal)
        router.post("/api/subnet/check")
                .handler(rbacAuthHandler.requirePermission("PERM_SUBNET_VIEW"))
                .handler(subnetHandler::check);

        // 3. Create Subnet (Validates, Detects Overlaps, Batch-Inserts IPs)
        router.post("/api/subnet")
                .handler(rbacAuthHandler.requirePermission("PERM_SUBNET_EDIT"))
                .handler(subnetHandler::create);

        // 4. Get Subnet By ID (With IP Status Breakdown)
        router.get("/api/subnet/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_SUBNET_VIEW"))
                .handler(subnetHandler::getById);

        // 5. Get Paginated IPs in Subnet
        router.get("/api/subnet/:id/ips")
                .handler(rbacAuthHandler.requirePermission("PERM_SUBNET_VIEW"))
                .handler(subnetHandler::getIps);

        // 6. Delete Subnet (Cascades Deletion of IPs)
        router.delete("/api/subnet/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_SUBNET_DELETE"))
                .handler(subnetHandler::delete);

        // 7. Live Subnet ICMP Ping Scan (On-Demand)
        router.post("/api/subnet/:id/scan")
                .handler(rbacAuthHandler.requirePermission("PERM_DISCOVERY_WRITE"))
                .handler(scannerHandler::triggerScan);

        // 8. Get Subnet Scan Status
        router.get("/api/subnet/scan-status/active")
                .handler(rbacAuthHandler.requirePermission("PERM_SUBNET_VIEW"))
                .handler(scannerHandler::getGlobalScanStatus);

        router.get("/api/subnet/:id/scan-status")
                .handler(rbacAuthHandler.requirePermission("PERM_SUBNET_VIEW"))
                .handler(scannerHandler::getScanStatus);

    }

}
