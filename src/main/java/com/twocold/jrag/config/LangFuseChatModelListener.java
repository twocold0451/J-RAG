package com.twocold.jrag.config;

import com.twocold.jrag.service.LangFuseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LangFuseChatModelListener implements ChatModelListener {

    private final LangFuseService langFuseService;
    private final ObjectMapper objectMapper; // Use the primary ObjectMapper (or create a new one if needed)
    
    // Attributes keys
    public static final String LANGFUSE_TRACE_ID = "langfuse_trace_id";
    public static final String LANGFUSE_PARENT_ID = "langfuse_parent_id";
    public static final String START_TIME = "start_time";
    public static final String GENERATION_NAME = "generation_name";

    /**
     * 将 LangChain4j 的消息对象转换为简单的 Map 列表，以便于 LangFuse 序列化展示。
     */
    private Object extractMessages(List<ChatMessage> messages) {
        if (messages == null) return null;
        return messages.stream().map(msg -> {
            Map<String, Object> map = new HashMap<>();
            // 使用小写的 role 名称，符合多数 LLM API 习惯
            String role = msg.type().toString().toLowerCase();
            map.put("role", role);

            switch (msg) {
                case UserMessage um -> {
                    String text = um.contents().stream()
                            .filter(c -> c instanceof TextContent)
                            .map(c -> ((TextContent) c).text())
                            .collect(Collectors.joining("\n"));
                    map.put("content", text);
                }
                case AiMessage am -> {
                    map.put("content", am.text());
                    if (am.toolExecutionRequests() != null && !am.toolExecutionRequests().isEmpty()) {
                        List<Map<String, Object>> toolCalls = am.toolExecutionRequests().stream().map(req -> {
                            Map<String, Object> toolCall = new HashMap<>();
                            toolCall.put("name", req.name());
                            toolCall.put("id", req.id());
                            // 将 arguments 转为 JSON 字符串避免序列化问题
                            if (req.arguments() != null) {
                                toolCall.put("arguments", req.arguments());
                            }
                            return toolCall;
                        }).collect(Collectors.toList());
                        map.put("tool_calls", toolCalls);
                    }
                }
                case SystemMessage sm -> map.put("content", sm.text());
                case ToolExecutionResultMessage tm -> {
                    map.put("tool_call_id", tm.id());
                    map.put("content", tm.text());
                }
                default ->
                    // 回退方案：如果类型未知，尝试通过 toString 获取内容
                        map.put("content", msg.toString());
            }
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(START_TIME, Instant.now());
        
        String traceId = TraceContext.getTraceId();
        String parentId = TraceContext.getCurrentSpanId();
        String name = TraceContext.getNextGenerationName();
        
        log.info("LangFuseListener onRequest: TraceId={}, ParentId={}, Name={}", traceId, parentId, name);

        if (traceId != null) {
            requestContext.attributes().put(LANGFUSE_TRACE_ID, traceId);
        }
        if (parentId != null) {
            requestContext.attributes().put(LANGFUSE_PARENT_ID, parentId);
        }
        if (name != null) {
            requestContext.attributes().put(GENERATION_NAME, name);
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        String traceId = (String) responseContext.attributes().get(LANGFUSE_TRACE_ID);
        String parentId = (String) responseContext.attributes().get(LANGFUSE_PARENT_ID);
        String name = (String) responseContext.attributes().get(GENERATION_NAME);
        Instant startTime = (Instant) responseContext.attributes().get(START_TIME);
        
        if (startTime == null) startTime = Instant.now();
        Instant endTime = Instant.now();
        
        if (name == null) name = "LLM Call"; 
        
        // 如果 Attributes 里没拿到（比如异步丢失），尝试从 ThreadLocal 补救
        if (traceId == null) traceId = TraceContext.getTraceId();
        if (parentId == null) parentId = TraceContext.getCurrentSpanId();
        
        // 兜底：如果还是没 ID，生成一个随机的，确保数据能上报
        if (traceId == null) traceId = UUID.randomUUID().toString();

        Map<String, Object> usage = new HashMap<>();
        if (responseContext.chatResponse().tokenUsage() != null) {
            usage.put("input", responseContext.chatResponse().tokenUsage().inputTokenCount());
            usage.put("output", responseContext.chatResponse().tokenUsage().outputTokenCount());
            usage.put("total", responseContext.chatResponse().tokenUsage().totalTokenCount());
        }

        Object extractedInput = extractMessages(responseContext.chatRequest().messages());
        String extractedOutput = responseContext.chatResponse().aiMessage().text();

        try {
            log.debug("LangFuseListener onResponse: Input={}, Output={}", 
                objectMapper.writeValueAsString(extractedInput), 
                extractedOutput);
        } catch (Exception e) {
            log.warn("Failed to log extracted messages", e);
        }

        langFuseService.createGeneration(
                UUID.randomUUID().toString(),
                traceId,
                parentId,
                name,
                responseContext.chatResponse().modelName(),
                extractedInput, // 关键修复：手动序列化输入消息
                extractedOutput, // 关键修复：直接取字符串作为输出
                usage,
                startTime,
                endTime
        );
        
        // 清理当前线程的 Generation 名称，防止干扰下一次不带名称的调用
        TraceContext.clearNextGenerationName();
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        log.error("LLM Error captured by LangFuse listener: {}", errorContext.error().getMessage());
        TraceContext.clearNextGenerationName();
    }
}
