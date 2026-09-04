package com.motadata.ipam.subnet;

import com.motadata.ipam.scanner.GoPluginBridge;

import com.motadata.ipam.scanner.ScannerHandler;

import com.motadata.ipam.security.RbacAuthHandler;

import io.vertx.core.Vertx;

import io.vertx.ext.web.Router;

import io.vertx.sqlclient.Pool;

/**
 * Modular Subnet Router.
 * Maps REST endpoints for Subnet CRUD, IP address queries, instant overlap validation,
 * and live ICMP network scanning with granular RBAC permissions.
 */
public class SubnetRouter {

    public static void register(Router router, Pool mysqlPool, GoPluginBridge goPluginBridge, Vertx vertx, RbacAuthHandler rbacAuthHandler) {

        SubnetService subnetService = new SubnetService(mysqlPool, vertx);

        SubnetHandler subnetHandler = new SubnetHandler(subnetService);

        ScannerHandler scannerHandler = new ScannerHandler(vertx);

        // 1. List Subnets (Paginated, Searchable)
        router.get("/api/subnet")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_READ"))
                .handler(subnetHandler::list);

        // 2. Instant Overlap & CIDR Check (For Frontend Modal)
        router.post("/api/subnet/check")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_READ"))
                .handler(subnetHandler::check);

        // 3. Create Subnet (Validates, Detects Overlaps, Batch-Inserts IPs)
        router.post("/api/subnet")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_WRITE"))
                .handler(subnetHandler::create);

        // 4. Get Subnet By ID (With IP Status Breakdown)
        router.get("/api/subnet/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_READ"))
                .handler(subnetHandler::getById);

        // 5. Get Paginated IPs in Subnet
        router.get("/api/subnet/:id/ips")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_READ"))
                .handler(subnetHandler::getIps);

        // 5b. Get Specific IP Details by IP Record ID
        router.get("/api/subnet/ip/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_READ"))
                .handler(subnetHandler::getIpDetails);

        // 5c. Get Audit Change Logs for Specific IP Record ID
        router.get("/api/subnet/ip/:id/changelog")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_READ"))
                .handler(subnetHandler::getIpChangeLogs);

        // 5d. Update IP Range Status (Available, Used, Transient, Reserved)

        router.post("/api/subnet/ip/range/status")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_WRITE"))
                .handler(subnetHandler::updateIpRangeStatus);

        // 5d. Delete IP Range (Reset to Available)
        router.post("/api/subnet/ip/range/delete")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_WRITE"))
                .handler(subnetHandler::deleteIpRange);

        // 5e. Bulk Delete IPs (Reset to Available)
        router.delete("/api/subnet/ips")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_WRITE"))
                .handler(subnetHandler::deleteMultipleIps);

        // 6. Delete Subnet (Cascades Deletion of IPs)
        router.delete("/api/subnet/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_WRITE"))
                .handler(subnetHandler::delete);

        // 7. Live Subnet ICMP Ping Scan (On-Demand - Requires Dashboard Write)
        router.post("/api/subnet/:id/scan")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_WRITE"))
                .handler(scannerHandler::triggerScan);

        // 8. Get Subnet Scan Status
        router.get("/api/subnet/scan-status/active")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_READ"))
                .handler(scannerHandler::getGlobalScanStatus);

        router.get("/api/subnet/:id/scan-status")
                .handler(rbacAuthHandler.requirePermission("PERM_DASHBOARD_READ"))
                .handler(scannerHandler::getScanStatus);

    }

}
