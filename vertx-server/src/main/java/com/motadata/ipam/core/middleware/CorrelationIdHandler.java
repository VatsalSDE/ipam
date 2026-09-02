package com.motadata.ipam.core.middleware;

import io.vertx.core.Handler;

import io.vertx.ext.web.RoutingContext;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Enterprise Correlation ID Middleware for end-to-end distributed observability.
 * Captures or generates unique request tracking IDs, injects them into HTTP headers,
 * Vert.x RoutingContext, and SLF4J MDC for unified logging.
 */
public class CorrelationIdHandler implements Handler<RoutingContext> {

    public static final String HEADER_NAME = "X-Correlation-ID";

    public static final String ALT_HEADER_NAME = "X-Request-ID";

    public static final String CONTEXT_KEY = "correlationId";

    public static CorrelationIdHandler create() {

        return new CorrelationIdHandler();

    }

    @Override
    public void handle(RoutingContext ctx) {

        String correlationId = ctx.request().getHeader(HEADER_NAME);

        if (correlationId == null || correlationId.isBlank()) {

            correlationId = ctx.request().getHeader(ALT_HEADER_NAME);

        }

        if (correlationId == null || correlationId.isBlank()) {

            correlationId = UUID.randomUUID().toString().substring(0, 18);

        } else {

            correlationId = correlationId.trim();

        }

        ctx.put(CONTEXT_KEY, correlationId);

        ctx.response().putHeader(HEADER_NAME, correlationId);

        MDC.put(CONTEXT_KEY, correlationId);

        ctx.addEndHandler(v -> MDC.remove(CONTEXT_KEY));

        ctx.next();

    }

    /**
     * Static helper to safely extract current correlation ID from context.
     */
    public static String get(RoutingContext ctx) {

        if (ctx == null) {

            return "unknown";

        }

        String id = ctx.get(CONTEXT_KEY);

        return id != null ? id : "unknown";

    }

}
