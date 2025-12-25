package com.example.qarag.config;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 手动实现 OTel 监听器，用于追踪 LangChain4j 的 LLM 调用。
 * 解决了 langchain4j-open-telemetry 依赖不可用的问题。
 */
@Component
@RequiredArgsConstructor
public class OpenTelemetryChatModelListener implements ChatModelListener {

    private final Tracer tracer;
    private static final String SPAN_KEY = "otel_span";
    private static final String SCOPE_KEY = "otel_scope";

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        String name = TraceContext.getNextGenerationName();
        if (name == null) name = "llm_call";

        // 创建子 Span
        Span span = tracer.spanBuilder(name)
                .setAttribute("ai.model.name", requestContext.chatRequest().modelName())
                .startSpan();
        
        requestContext.attributes().put(SPAN_KEY, span);
        
        // 开启 Scope 使其在当前线程生效
        Scope scope = span.makeCurrent();
        requestContext.attributes().put(SCOPE_KEY, scope);
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Span span = (Span) responseContext.attributes().get(SPAN_KEY);
        Scope scope = (Scope) responseContext.attributes().get(SCOPE_KEY);
        
        if (span != null) {
            if (responseContext.chatResponse().tokenUsage() != null) {
                span.setAttribute("ai.usage.input_tokens", responseContext.chatResponse().tokenUsage().inputTokenCount());
                span.setAttribute("ai.usage.output_tokens", responseContext.chatResponse().tokenUsage().outputTokenCount());
                span.setAttribute("ai.usage.total_tokens", responseContext.chatResponse().tokenUsage().totalTokenCount());
            }
            span.end();
        }
        if (scope != null) {
            scope.close();
        }
        TraceContext.clearNextGenerationName();
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Span span = (Span) errorContext.attributes().get(SPAN_KEY);
        Scope scope = (Scope) errorContext.attributes().get(SCOPE_KEY);
        
        if (span != null) {
            span.recordException(errorContext.error());
            span.end();
        }
        if (scope != null) {
            scope.close();
        }
        TraceContext.clearNextGenerationName();
    }
}
