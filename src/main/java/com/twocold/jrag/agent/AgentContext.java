package com.twocold.jrag.agent;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agent 上下文容器
 * 使用 ThreadLocal 在 Agent 的执行线程中传递当前会话关联的文档 ID 以及 SSE 汇聚点。
 */
public class AgentContext {
    private static final ThreadLocal<List<UUID>> DOCUMENT_IDS = new ThreadLocal<>();
    private static final ThreadLocal<Sinks.Many<ServerSentEvent<String>>> CURRENT_SINK = new ThreadLocal<>();

    /**
     * 设置当前上下文的文档 ID 列表
     */
    public static void setDocumentIds(List<UUID> documentIds) {
        DOCUMENT_IDS.set(documentIds != null ? documentIds : new ArrayList<>());
    }

    /**
     * 获取当前上下文的文档 ID 列表
     */
    public static List<UUID> getDocumentIds() {
        List<UUID> ids = DOCUMENT_IDS.get();
        return ids != null ? ids : new ArrayList<>();
    }

    /**
     * 设置当前请求的 SSE Sink
     */
    public static void setSink(Sinks.Many<ServerSentEvent<String>> sink) {
        CURRENT_SINK.set(sink);
    }

    /**
     * 获取当前请求的 SSE Sink
     */
    public static Sinks.Many<ServerSentEvent<String>> getSink() {
        return CURRENT_SINK.get();
    }

    /**
     * 向前端发送思考过程事件
     */
    public static void emitThought(String thought) {
        Sinks.Many<ServerSentEvent<String>> sink = getSink();
        if (sink != null) {
            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("thought")
                    .data(thought)
                    .build());
        }
    }

    /**
     * 清理 ThreadLocal，防止内存泄漏
     */
    public static void clear() {
        DOCUMENT_IDS.remove();
        CURRENT_SINK.remove();
    }
}