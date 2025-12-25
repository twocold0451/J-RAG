package com.example.qarag.config;

import io.opentelemetry.api.trace.Span;

public class TraceContext {
    private static final ThreadLocal<String> NEXT_GENERATION_NAME = new ThreadLocal<>();

    public static void setNextGenerationName(String name) {
        NEXT_GENERATION_NAME.set(name);
    }

    public static String getNextGenerationName() {
        return NEXT_GENERATION_NAME.get();
    }

    public static void clearNextGenerationName() {
        NEXT_GENERATION_NAME.remove();
    }

    public static void startTrace(String traceId) {
        // No-op: OpenTelemetry manages trace lifecycle
    }

    public static String getTraceId() {
        Span current = Span.current();
        if (current.getSpanContext().isValid()) {
            return current.getSpanContext().getTraceId();
        }
        return null;
    }

    public static void pushSpan(String spanId) {
        // No-op: Managed via standard OTel scopes (try-with-resources)
    }

    public static String popSpan() {
        // No-op
        return null;
    }

    public static String getCurrentSpanId() {
        Span current = Span.current();
        if (current.getSpanContext().isValid()) {
            return current.getSpanContext().getSpanId();
        }
        return null;
    }

    public static void clear() {
        NEXT_GENERATION_NAME.remove();
    }
}
