package com.motadata.ipam.request;


import com.motadata.ipam.core.model.ApiResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * REST Request Handler for IP Requests.
 * Integrates with RBAC, validates request payloads, and delegates to IpRequestService.
 */
public class IpRequestHandler {

    private final IpRequestService ipRequestService;

    public IpRequestHandler(IpRequestService ipRequestService) {
        this.ipRequestService = ipRequestService;
    }

    /**
     * GET /api/ip-requests, GET /api/ipRequests
     * Lists IP requests (All for admin, user's own for non-admin).
     */
    public void listRequests(RoutingContext ctx) {
        String username = extractUsername(ctx);
        boolean isAdmin = checkIsAdmin(ctx);

        ipRequestService.listRequests(username, isAdmin)
                .onSuccess(requests -> ApiResponse.sendSuccess(ctx, requests))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "LIST_REQUESTS_FAILED", err.getMessage()));
    }

    /**
     * GET /api/ip-requests/:id, GET /api/ipRequests/:id
     * Retrieves single IP request details.
     */
    public void getRequestById(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Invalid IP request ID");
            return;
        }

        ipRequestService.getRequestById(id)
                .onSuccess(request -> ApiResponse.sendSuccess(ctx, request))
                .onFailure(err -> ApiResponse.sendError(ctx, 404, "NOT_FOUND", err.getMessage()));
    }

    /**
     * POST /api/ip-requests, POST /api/ipRequests
     * Creates a new IP allocation request.
     */
    public void createRequest(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Request body cannot be empty");
            return;
        }

        String username = extractUsername(ctx);

        ipRequestService.createRequest(body, username)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, 200, result.getString("message", "IP Request added successfully"), result))
                .onFailure(err -> ApiResponse.sendError(ctx, 400, "CREATE_REQUEST_FAILED", err.getMessage()));
    }

    /**
     * POST /api/ip-requests/approved, POST /api/ipRequests/approved
     * Approves an IP request and allocates IPs.
     */
    public void approveRequest(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Request body cannot be empty");
            return;
        }

        Long id = parseId(body.getValue("id") != null ? String.valueOf(body.getValue("id")) : ctx.pathParam("id"));
        if (id == null) {
            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Invalid or missing IP request ID");
            return;
        }

        String adminUsername = extractUsername(ctx);

        ipRequestService.approveRequest(id, body, adminUsername)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, 200, result.getString("message", "IP Request has been approved successfully."), result))
                .onFailure(err -> ApiResponse.sendError(ctx, 400, "APPROVE_FAILED", err.getMessage()));
    }

    /**
     * POST /api/ip-requests/rejected, POST /api/ipRequests/rejected
     * Rejects an IP request with a remark.
     */
    public void rejectRequest(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Request body cannot be empty");
            return;
        }

        Long id = parseId(body.getValue("id") != null ? String.valueOf(body.getValue("id")) : ctx.pathParam("id"));
        if (id == null) {
            ApiResponse.sendError(ctx, 400, "BAD_REQUEST", "Invalid or missing IP request ID");
            return;
        }

        String remark = body.getString("remark", "Rejected by administrator");
        String adminUsername = extractUsername(ctx);

        ipRequestService.rejectRequest(id, remark, adminUsername)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, 200, result.getString("message", "IP Request has been rejected successfully."), result))
                .onFailure(err -> ApiResponse.sendError(ctx, 400, "REJECT_FAILED", err.getMessage()));
    }

    private Long parseId(String val) {
        if (val == null || val.trim().isEmpty() || "null".equalsIgnoreCase(val.trim())) return null;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractUsername(RoutingContext ctx) {
        if (ctx.user() != null && ctx.user().principal() != null) {
            String sub = ctx.user().principal().getString("sub");
            if (sub != null && !sub.isEmpty()) return sub;
            String username = ctx.user().principal().getString("username");
            if (username != null && !username.isEmpty()) return username;
        }
        return "admin";
    }

    private boolean checkIsAdmin(RoutingContext ctx) {
        if (ctx.user() != null && ctx.user().principal() != null) {
            JsonArray perms = ctx.user().principal().getJsonArray("permissions");
            if (perms != null) {
                return perms.contains("ROLE_ADMIN") || perms.contains("PERM_ALL") || perms.contains("ALL");
            }
            String roleName = ctx.user().principal().getString("roleName");
            return "ROLE_ADMIN".equalsIgnoreCase(roleName) || "ADMIN".equalsIgnoreCase(roleName);
        }
        return true;
    }
}
