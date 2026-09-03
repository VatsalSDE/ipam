package com.motadata.ipam.dashboard;

import com.motadata.ipam.core.model.ApiResponse;
import com.motadata.ipam.security.RbacAuthHandler;
import io.vertx.ext.web.Router;

/**
 * REST Router for IPAM Dashboard operations.
 * Exposes unified, resilient summary stats for executive and operational views.
 */
public class DashboardRouter {

    public static void register(Router router, DashboardService dashboardService, RbacAuthHandler rbacAuthHandler) {

        // GET /api/dashboard/summary - Resilient unified dashboard metrics
        router.get("/api/dashboard/summary")
                .handler(rbacAuthHandler.requirePermission("PERM_SUBNET_VIEW"))
                .handler(ctx -> dashboardService.getDashboardSummary()
                        .onSuccess(data -> ApiResponse.sendSuccess(ctx, data))
                        .onFailure(err -> ApiResponse.sendError(ctx, 500, "DASHBOARD_FETCH_FAILED", err.getMessage())));

    }

}
