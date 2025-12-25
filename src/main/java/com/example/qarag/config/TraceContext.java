package com.example.qarag.config;

import java.util.Stack;
import java.util.UUID;

public class TraceContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Stack<String>> SPAN_STACK = ThreadLocal.withInitial(Stack::new);
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
        TRACE_ID.set(traceId != null ? traceId : UUID.randomUUID().toString());
        SPAN_STACK.get().clear();
    }

    public static String getTraceId() {
        return TRACE_ID.get();
    }

    public static void pushSpan(String spanId) {
        SPAN_STACK.get().push(spanId);
    }

    public static String popSpan() {
        Stack<String> stack = SPAN_STACK.get();
        return stack.isEmpty() ? null : stack.pop();
    }

    public static String getCurrentSpanId() {
        Stack<String> stack = SPAN_STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    public static void clear() {
        TRACE_ID.remove();
        SPAN_STACK.remove();
    }
}
