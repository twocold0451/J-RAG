package com.example.qarag.config;

import java.util.ArrayDeque;
import java.util.Deque;

public class TraceContext {
    private static final ThreadLocal<String> NEXT_GENERATION_NAME = new ThreadLocal<>();
    private static final ThreadLocal<String> MANUAL_TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Deque<String>> SPAN_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static void setNextGenerationName(String name) {
        NEXT_GENERATION_NAME.set(name);
    }

    public static String getNextGenerationName() {
        return NEXT_GENERATION_NAME.get();
    }

    public static void clearNextGenerationName() {
        NEXT_GENERATION_NAME.remove();
    }

    /**
     * Set the global Trace ID for the current thread/request.
     */
    public static void setTraceId(String traceId) {
        MANUAL_TRACE_ID.set(traceId);
    }

    /**
     * Get the current Trace ID.
     * Priority:
     * 1. Manually set Trace ID (e.g. from LangFuse logic)
     * 2. OpenTelemetry active Span Trace ID
     */
    public static String getTraceId() {
        return MANUAL_TRACE_ID.get();
    }

    /**
     * Push a new Span ID onto the stack.
     */
    public static void pushSpan(String spanId) {
        SPAN_STACK.get().push(spanId);
    }

    /**
     * Pop the current Span ID from the stack.
     */
    public static String popSpan() {
        Deque<String> stack = SPAN_STACK.get();
        if (!stack.isEmpty()) {
            return stack.pop();
        }
        return null;
    }

    /**
     * Get the current (parent) Span ID.
     * Priority:
     * 1. Top of the manual stack
     * 2. OpenTelemetry active Span ID
     */
    public static String getCurrentSpanId() {
        Deque<String> stack = SPAN_STACK.get();
        if (!stack.isEmpty()) {
            return stack.peek();
        }

        return null;
    }

    public static void clear() {
        NEXT_GENERATION_NAME.remove();
        MANUAL_TRACE_ID.remove();
        SPAN_STACK.remove();
    }
}