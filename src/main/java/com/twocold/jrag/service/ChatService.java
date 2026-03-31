package com.twocold.jrag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twocold.jrag.agent.DeepThinkingAgent;
import com.twocold.jrag.agent.RagAgentTools;
import com.twocold.jrag.config.Observed;
import com.twocold.jrag.config.RagProperties;
import com.twocold.jrag.config.TraceContext;
import com.twocold.jrag.domain.ChatMessage;
import com.twocold.jrag.domain.Chunk;
import com.twocold.jrag.domain.Conversation;
import com.twocold.jrag.repository.ChatMessageRepository;
import com.twocold.jrag.repository.ConversationRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 核心聊天服务
 * 负责编排 RAG 完整流程：查询重写 -> 复杂问题分解 -> 混合搜索 -> 重排序 -> LLM 响应生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_CONTEXT_MESSAGES = 10;
    private static final String GENERIC_STREAM_ERROR_MESSAGE = "抱歉，服务暂时繁忙，请稍后重试。";

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StreamingChatModel openAiStreamingChatModel;
    private final JdbcTemplate jdbcTemplate;
    private final RetrievalService retrievalService;
    private final QueryRewriteService queryRewriteService;
    private final QueryDecompositionService queryDecompositionService;
    private final LangFuseService langFuseService;
    private final RagProperties ragProperties;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    /**
     * 执行流式 RAG 对话
     */
    @Transactional
    @Observed(name = "Chat Interaction", includeInputFields = {"conversationId", "userId", "userMessageContent"})
    public Flux<ServerSentEvent<String>> streamChat(Long conversationId, Long userId, String userMessageContent, boolean useDeepThinking) {
        String traceId = TraceContext.getTraceId();

        // 同步准备上下文
        ChatContext context = prepareChatContext(conversationId, userId, userMessageContent, traceId);
        if (context.existingResponse() != null) {
            // 幂等性命中，直接返回缓存的响应
            return Flux.just(ServerSentEvent.builder(context.existingResponse()).build());
        }

        // 分支处理
        Flux<ServerSentEvent<String>> responseFlux = useDeepThinking
                ? handleDeepThinking(context)
                : handleStandardRag(context);

        // 统一错误处理和清理
        return responseFlux
                .doFinally(signal -> TraceContext.clear())
                .onErrorResume(error -> {
                    log.error("streamChat 处理失败", error);
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event("error")
                                    .data(GENERIC_STREAM_ERROR_MESSAGE)
                                    .build()
                    );
                });
    }

    // ============ 同步准备逻辑 ============

    private record ChatContext(
            Long conversationId,
            Long userId,
            String userMessageContent,
            String traceId,
            Conversation conversation,
            List<ChatMessage> latestMessages,
            List<UUID> associatedDocumentIds,
            int maxRewriteContext,
            String existingResponse  // 幂等性命中的缓存响应
    ) {}

    private ChatContext prepareChatContext(Long conversationId, Long userId, String userMessageContent, String traceId) {
        // 注册 LangFuse Trace
        langFuseService.createTrace(traceId, "Chat Interaction", userId.toString(), Map.of("conversationId", conversationId));

        // 验证对话
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));

        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权访问该对话");
        }

        // 加载历史记录
        int maxRewriteContext = (ragProperties.retrieval().rewrite() != null && ragProperties.retrieval().rewrite().enabled())
                ? ragProperties.retrieval().rewrite().maxContextMessages()
                : 0;
        int maxHistoryLimit = Math.max(MAX_CONTEXT_MESSAGES, maxRewriteContext);
        List<ChatMessage> latestMessages = chatMessageRepository.findLatestMessagesByConversationId(conversationId, maxHistoryLimit);

        // 幂等性检查
        String existingResponse = checkIdempotency(latestMessages, userMessageContent);
        if (existingResponse != null) {
            return new ChatContext(conversationId, userId, userMessageContent, traceId,
                    conversation, latestMessages, null, maxRewriteContext, existingResponse);
        }

        // 保存用户消息
        saveUserMessage(conversationId, userMessageContent);

        // 获取关联文档
        Long parentId = conversation.getParentId();
        Long effectiveParentId = parentId != null ? parentId : conversationId;
        List<UUID> associatedDocumentIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT document_id FROM conversation_documents WHERE conversation_id = ? OR conversation_id = ?",
                UUID.class, conversationId, effectiveParentId);

        return new ChatContext(conversationId, userId, userMessageContent, traceId,
                conversation, latestMessages, associatedDocumentIds, maxRewriteContext, null);
    }

    private String checkIdempotency(List<ChatMessage> latestMessages, String userMessageContent) {
        int idempotencyLimit = Math.min(latestMessages.size(), 10);
        for (int i = 0; i < idempotencyLimit; i++) {
            ChatMessage msg = latestMessages.get(i);
            if ("USER".equals(msg.getRole())
                    && msg.getContent().equals(userMessageContent)
                    && msg.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(30))) {
                int index = latestMessages.indexOf(msg);
                if (index > 0) {
                    ChatMessage potentialAiReply = latestMessages.get(index - 1);
                    if ("ASSISTANT".equals(potentialAiReply.getRole())) {
                        return potentialAiReply.getContent();
                    }
                }
            }
        }
        return null;
    }

    private void saveUserMessage(Long conversationId, String content) {
        ChatMessage userChatMessage = new ChatMessage();
        userChatMessage.setConversationId(conversationId);
        userChatMessage.setRole("USER");
        userChatMessage.setContent(content);
        userChatMessage.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(userChatMessage);
    }

    // ============ 深度思考模式 ============

    private Flux<ServerSentEvent<String>> handleDeepThinking(ChatContext ctx) {
        log.info("启动深度思考模式处理会话: {}", ctx.conversationId());

        List<dev.langchain4j.data.message.ChatMessage> agentMessages = buildAgentMessages(ctx);

        // 使用 Sinks 桥接 LangChain4j 回调到 Flux
        return Flux.<ServerSentEvent<String>>create(sink -> {
            RagAgentTools tools = new RagAgentTools(
                    retrievalService, queryDecompositionService, sink, ctx.associatedDocumentIds(), objectMapper);

            DeepThinkingAgent agent = AiServices.builder(DeepThinkingAgent.class)
                    .streamingChatModel(openAiStreamingChatModel)
                    .tools(tools)
                    .build();

            // Agent 返回 Flux<String>，订阅并转发到 sink
            agent.chat(agentMessages)
                    .timeout(Duration.ofSeconds(300))
                    .doOnNext(token -> sink.next(ServerSentEvent.<String>builder()
                            .event("message")
                            .data(token)
                            .build()))
                    .reduce(new StringBuilder(), StringBuilder::append)
                    .doOnSuccess(fullAnswer -> {
                        saveAiMessage(ctx.conversationId(), fullAnswer.toString(), null);
                        updateConversationTime(ctx.conversation());
                        sink.complete();
                    })
                    .doOnError(error -> {
                        log.error("深度思考模式流处理失败", error);
                        if (error instanceof TimeoutException) {
                            sink.next(ServerSentEvent.<String>builder()
                                    .event("message")
                                    .data("抱歉，思考过程超时，请尝试简化问题或提供更多上下文。")
                                    .build());
                        }
                        sink.error(error);
                    })
                    .subscribe();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private List<dev.langchain4j.data.message.ChatMessage> buildAgentMessages(ChatContext ctx) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

        String systemPrompt = promptService.getPrompt("deep_thinking_agent", """
                你是一个具备深度思考能力的 RAG 智能助手。你的目标是利用可用工具准确回答用户的问题。

                请遵循以下思考流程 (ReAct)：
                1. **Analyze (分析)**: 仔细分析用户的问题，判断是否需要拆解复杂问题或直接搜索。
                2. **Act (行动)**: 根据分析结果，选择合适的工具（searchKnowledgeBase 或 decomposeQuery）。
                3. **Observe (观察)**: 观察工具返回的结果。
                4. **Reason (推理)**: 基于观察到的信息，判断是否足够回答用户问题。如果不够，决定下一步行动。
                5. **Reply (回答)**: 当收集到足够信息后，综合整理并给出最终答案。

                注意：优先使用事实数据回答，严禁编造年份，最终回答要条理清晰。
                """);
        messages.add(SystemMessage.from(systemPrompt));

        ctx.latestMessages().stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .forEach(msg -> {
                    if ("USER".equals(msg.getRole())) messages.add(UserMessage.from(msg.getContent()));
                    else if ("ASSISTANT".equals(msg.getRole())) messages.add(AiMessage.from(msg.getContent()));
                });
        messages.add(UserMessage.from(ctx.userMessageContent()));

        return messages;
    }

    // ============ 标准 RAG 模式 ============

    private Flux<ServerSentEvent<String>> handleStandardRag(ChatContext ctx) {
        RetrievalResult retrieval = executeRetrieval(ctx);

        // 构建 sources 事件
        Mono<ServerSentEvent<String>> sourcesEvent = retrieval.sources().isEmpty()
                ? Mono.empty()
                : Mono.just(ServerSentEvent.<String>builder()
                        .event("sources")
                        .data(convertToJsonSilently(objectMapper, retrieval.sources()))
                        .build());

        // 使用 Sinks 桥接 LLM 流式响应
        Flux<ServerSentEvent<String>> messageFlux = Flux.<ServerSentEvent<String>>create(sink -> {
            List<dev.langchain4j.data.message.ChatMessage> messages = buildPromptMessages(
                    ctx.latestMessages(), retrieval.textSegments(), ctx.userMessageContent());

            TraceContext.setNextGenerationName("LLM: Final Generation");
            openAiStreamingChatModel.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    sink.next(ServerSentEvent.<String>builder()
                            .event("message")
                            .data(token)
                            .build());
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    handleChatCompletion(ctx, retrieval, response);
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    sink.error(error);
                }
            });
        }).subscribeOn(Schedulers.boundedElastic());

        return Flux.concat(sourcesEvent, messageFlux);
    }

    private record RetrievalResult(
            List<String> textSegments,
            List<Map<String, Object>> sources,
            String rewrittenQuery
    ) {}

    private RetrievalResult executeRetrieval(ChatContext ctx) {
        if (ctx.associatedDocumentIds().isEmpty()) {
            return new RetrievalResult(Collections.emptyList(), Collections.emptyList(), null);
        }

        String searchKeyword = ctx.userMessageContent();

        // 查询重写
        if (ctx.maxRewriteContext() > 0 && !ctx.latestMessages().isEmpty()) {
            List<ChatMessage> chronologicalHistory = ctx.latestMessages().stream()
                    .limit(ctx.maxRewriteContext())
                    .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                    .collect(Collectors.toList());
            searchKeyword = queryRewriteService.rewriteIfNecessary(ctx.userMessageContent(), chronologicalHistory);
        }

        // 查询分解和批量检索
        List<String> subQueries = queryDecompositionService.decompose(searchKeyword);
        List<Chunk> chunks = retrievalService.batchHybridSearch(subQueries, ctx.associatedDocumentIds());

        List<String> textSegments = new ArrayList<>();
        List<Map<String, Object>> sources = new ArrayList<>();

        for (Chunk chunk : chunks) {
            textSegments.add(chunk.getContent());
            sources.add(extractSourceInfo(chunk));
        }

        return new RetrievalResult(textSegments, sources, searchKeyword);
    }

    private List<dev.langchain4j.data.message.ChatMessage> buildPromptMessages(
            List<ChatMessage> latestMessages, List<String> textSegments, String userMessageContent) {

        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        latestMessages.stream()
                .limit(MAX_CONTEXT_MESSAGES)
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .forEach(msg -> {
                    if ("USER".equals(msg.getRole())) messages.add(new UserMessage(msg.getContent()));
                    else if ("ASSISTANT".equals(msg.getRole())) messages.add(new AiMessage(msg.getContent()));
                });

        String systemPrompt = promptService.getPrompt("standard_rag", """
                你是一个专业的智能助手，专门负责基于提供的上下文信息回答用户问题。

                ## 核心原则
                1. **基于事实回答**：严格基于提供的上下文信息回答问题，不添加外部知识
                2. **准确引用**：在回答中明确引用上下文中的相关信息和来源
                3. **信息完整性**：如果上下文信息不足以完全回答问题，请明确指出信息不足的部分
                4. **逻辑清晰**：回答结构清晰，逻辑连贯，便于理解

                ## 回答要求
                - 使用简洁明了的中文表达
                - 优先使用上下文中的专业术语和概念
                - 保持客观中立的态度，避免主观判断
                """);

        messages.addFirst(SystemMessage.systemMessage(systemPrompt));
        String context = textSegments.isEmpty() ? "" : "Context:\n" + String.join("\n---\n", textSegments);
        messages.add(new UserMessage(context + "\n\nQuestion: " + userMessageContent));

        return messages;
    }

    // ============ 辅助方法 ============

    private void handleChatCompletion(ChatContext ctx, RetrievalResult retrieval, ChatResponse response) {
        try {
            String aiResponseText = response.aiMessage().text();
            String sourcesJson = retrieval.sources().isEmpty() ? null : convertToJsonSilently(objectMapper, retrieval.sources());

            saveAiMessage(ctx.conversationId(), aiResponseText, sourcesJson);
            saveRagInteraction(ctx.traceId(), ctx.conversationId(), ctx.userId(),
                    ctx.userMessageContent(), retrieval.rewrittenQuery(), aiResponseText, sourcesJson);
            updateConversationTime(ctx.conversation());
        } catch (Exception e) {
            log.error("保存 RAG 交互日志失败", e);
        }
    }

    private Map<String, Object> extractSourceInfo(Chunk chunk) {
        Map<String, Object> sourceInfo = new HashMap<>();
        sourceInfo.put("id", chunk.getId());
        sourceInfo.put("documentId", chunk.getDocumentId());
        sourceInfo.put("score", chunk.getScore());
        sourceInfo.put("content", chunk.getContent());
        if (chunk.getSourceMeta() != null) {
            try {
                Map<String, Object> meta = objectMapper.readValue(chunk.getSourceMeta(), Map.class);
                sourceInfo.put("metadata", meta);
                if (meta.containsKey("file_name")) sourceInfo.put("fileName", meta.get("file_name"));
            } catch (Exception e) {
                log.warn("解析 chunk {} 的元数据失败", chunk.getId());
            }
        }
        return sourceInfo;
    }

    private void saveAiMessage(Long conversationId, String content, String sourcesJson) {
        ChatMessage aiChatMessage = new ChatMessage();
        aiChatMessage.setConversationId(conversationId);
        aiChatMessage.setRole("ASSISTANT");
        aiChatMessage.setContent(content);
        aiChatMessage.setCreatedAt(LocalDateTime.now());
        aiChatMessage.setSources(sourcesJson);
        chatMessageRepository.save(aiChatMessage);
    }

    private void saveRagInteraction(String traceId, Long conversationId, Long userId, String userQuery,
                                    String rewrittenQuery, String aiResponse, String sourcesJson) {
        String insertSql = """
            INSERT INTO rag_interactions
            (trace_id, conversation_id, user_id, user_query, rewritten_query, ai_response, retrieved_contexts, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """;
        jdbcTemplate.update(insertSql, traceId, conversationId, userId, userQuery,
                rewrittenQuery, aiResponse, sourcesJson, LocalDateTime.now());
    }

    private void updateConversationTime(Conversation conversation) {
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    private String convertToJsonSilently(ObjectMapper mapper, Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
