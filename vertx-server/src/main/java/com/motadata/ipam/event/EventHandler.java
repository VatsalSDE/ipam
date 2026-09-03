package com.motadata.ipam.event;


import com.motadata.ipam.core.model.ApiResponse;

import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

/**
 * REST API Handler for Event Notifications & Audit Log Querying.
 */
public class EventHandler {

    private static final Logger logger = LoggerFactory.getLogger(EventHandler.class);

    private final EventService eventService;

    public EventHandler(EventService eventService) {

        this.eventService = eventService;

    }

    /**
     * GET /api/event
     * Lists paginated event notifications.
     */
    public void list(RoutingContext ctx) {

        int limit = parseQueryParam(ctx, "limit", 50);

        int offset = parseQueryParam(ctx, "offset", 0);

        String tlStr = ctx.request().getParam("timeline");

        if (tlStr == null || tlStr.isBlank()) {

            tlStr = ctx.request().getParam("exportTimeline");

        }

        Integer timeline = null;

        if (tlStr != null && !tlStr.isBlank()) {

            try {

                timeline = Integer.parseInt(tlStr.trim());

            } catch (NumberFormatException ignored) {

            }

        }

        eventService.listEvents(limit, offset, timeline)
                .onSuccess(result -> ApiResponse.sendSuccess(ctx, result))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "EVENT_LIST_FAILED", err.getMessage()));

    }

    /**
     * GET /api/event/top
     * Lists top 25 recent events for dashboard.
     */
    public void listTop(RoutingContext ctx) {

        eventService.listTopEvents()
                .onSuccess(list -> ApiResponse.sendSuccess(ctx, list))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "TOP_EVENTS_FAILED", err.getMessage()));

    }

    /**
     * GET /api/event/summary12m
     * Lists 12-month event summary for Dashboard sparkline.
     */
    public void summary12m(RoutingContext ctx) {

        eventService.get12MonthEventSummary()
                .onSuccess(list -> ApiResponse.sendSuccess(ctx, list))
                .onFailure(err -> ApiResponse.sendError(ctx, 500, "EVENT_SUMMARY_FAILED", err.getMessage()));

    }

    private int parseQueryParam(RoutingContext ctx, String param, int defaultValue) {

        String val = ctx.request().getParam(param);

        if (val == null || val.isBlank()) {

            return defaultValue;

        }

        try {

            return Integer.parseInt(val.trim());

        } catch (NumberFormatException e) {

            return defaultValue;

        }

    }

}
