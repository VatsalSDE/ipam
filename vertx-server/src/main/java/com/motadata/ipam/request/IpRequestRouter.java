package com.motadata.ipam.request;


import com.motadata.ipam.security.RbacAuthHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.mysqlclient.MySQLPool;

/**
 * Modular IP Requests Router.
 * Registers REST routes for IP allocation requests, status queries, approvals, and rejections.
 */
public class IpRequestRouter {

    public static void register(Router router, MySQLPool mysqlPool, Vertx vertx, RbacAuthHandler rbacAuthHandler) {
        IpRequestService service = new IpRequestService(mysqlPool, vertx);
        IpRequestHandler handler = new IpRequestHandler(service);

        // 1. List IP Requests (Read permission)
        router.get("/api/ip-requests")
                .handler(rbacAuthHandler.requirePermission("PERM_IP REQUESTS_READ"))
                .handler(handler::listRequests);

        router.get("/api/ipRequests")
                .handler(rbacAuthHandler.requirePermission("PERM_IP REQUESTS_READ"))
                .handler(handler::listRequests);

        // 2. Get Single IP Request by ID (Read permission)
        router.get("/api/ip-requests/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_IP REQUESTS_READ"))
                .handler(handler::getRequestById);

        router.get("/api/ipRequests/:id")
                .handler(rbacAuthHandler.requirePermission("PERM_IP REQUESTS_READ"))
                .handler(handler::getRequestById);

        // 3. Create IP Request (Users with IP Requests read or write can create requests)
        router.post("/api/ip-requests")
                .handler(rbacAuthHandler.requirePermission("PERM_IP REQUESTS_READ"))
                .handler(handler::createRequest);

        router.post("/api/ipRequests")
                .handler(rbacAuthHandler.requirePermission("PERM_IP REQUESTS_READ"))
                .handler(handler::createRequest);

        // 4. Approve IP Request (Requires Write permission / Admin)
        router.post("/api/ip-requests/approved")
                .handler(rbacAuthHandler.requirePermission("PERM_IP REQUESTS_WRITE"))
                .handler(handler::approveRequest);

        router.post("/api/ipRequests/approved")
                .handler(rbacAuthHandler.requirePermission("PERM_IP REQUESTS_WRITE"))
                .handler(handler::approveRequest);

        // 5. Reject IP Request (Requires Write permission / Admin)
        router.post("/api/ip-requests/rejected")
                .handler(rbacAuthHandler.requirePermission("PERM_IP REQUESTS_WRITE"))
                .handler(handler::rejectRequest);

        router.post("/api/ipRequests/rejected")
                .handler(rbacAuthHandler.requirePermission("PERM_IP REQUESTS_WRITE"))
                .handler(handler::rejectRequest);
    }
}
