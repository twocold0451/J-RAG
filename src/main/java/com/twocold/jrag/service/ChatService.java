package com.twocold.jrag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twocold.jrag.agent.AgentContext;
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
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.*;
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

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StreamingChatModel openAiStreamingChatModel;
    private final JdbcTemplate jdbcTemplate;
    private final RetrievalService retrievalService;
    private final QueryRewriteService queryRewriteService;
    private final QueryDecompositionService queryDecompositionService;
    private final LangFuseService langFuseService;
    private final RagProperties ragProperties;
    // private final DeepThinkingAgent deepThinkingAgent; // Removed: Per-request instantiation
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    /**
     * 执行流式 RAG 对话
     *
     * @param conversationId 对话 ID
     * @param userId 用户 ID
     * @param userMessageContent 用户提问内容
     * @param useDeepThinking 是否启用深度思考（Agent）模式
     * @return SSE 事件流
     */
    @Transactional
    @Observed(name = "Chat Interaction", includeInputFields = {"conversationId", "userId", "userMessageContent"})
    public Flux<ServerSentEvent<String>> streamChat(Long conversationId, Long userId, String userMessageContent, boolean useDeepThinking) {
        return Flux.<ServerSentEvent<String>>create(sink -> {
            // 获取由 Aspect 生成的 Trace ID 用于全链路追踪
            String traceId = TraceContext.getTraceId();

            try {
                // 在 LangFuse 中注册 Trace
                langFuseService.createTrace(traceId, "Chat Interaction", userId.toString(), Map.of("conversationId", conversationId));

                Conversation conversation = conversationRepository.findById(conversationId)
                        .orElseThrow(() -> new IllegalArgumentException("未找到对话"));

                if (!conversation.getUserId().equals(userId)) {
                    throw new SecurityException("无权访问该对话");
                }

                // 加载历史记录用于查询重写
                int maxRewriteContext = (ragProperties.retrieval().rewrite() != null && ragProperties.retrieval().rewrite().enabled())
                        ? ragProperties.retrieval().rewrite().maxContextMessages()
                        : 0;
                int maxHistoryLimit = Math.max(MAX_CONTEXT_MESSAGES, maxRewriteContext);
                List<ChatMessage> latestMessages = chatMessageRepository.findLatestMessagesByConversationId(conversationId, maxHistoryLimit);

                // 幂等性检查：防止重复提交
                ChatMessage existingUserMsg = null;
                int idempotencyLimit = Math.min(latestMessages.size(), 10);
                for (int i = 0; i < idempotencyLimit; i++) {
                    ChatMessage msg = latestMessages.get(i);
                    if ("USER".equals(msg.getRole())
                            && msg.getContent().equals(userMessageContent)
                            && msg.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(30))) {
                        existingUserMsg = msg;
                        break;
                    }
                }

                if (existingUserMsg != null) {
                    int index = latestMessages.indexOf(existingUserMsg);
                    if (index > 0) {
                        ChatMessage potentialAiReply = latestMessages.get(index - 1);
                        if ("ASSISTANT".equals(potentialAiReply.getRole())) {
                            sink.next(ServerSentEvent.builder(potentialAiReply.getContent()).build());
                            sink.complete();
                            return;
                        }
                    }
                } else {
                    // 保存用户消息
                    ChatMessage userChatMessage = new ChatMessage();
                    userChatMessage.setConversationId(conversationId);
                    userChatMessage.setRole("USER");
                    userChatMessage.setContent(userMessageContent);
                    userChatMessage.setCreatedAt(LocalDateTime.now());
                    chatMessageRepository.save(userChatMessage);
                }

                // 获取对话关联的文档范围
                Long parentId = conversation.getParentId();
                Long effectiveParentId = parentId != null ? parentId : conversationId;
                List<UUID> associatedDocumentIds = jdbcTemplate.queryForList(
                        "SELECT DISTINCT document_id FROM conversation_documents WHERE conversation_id = ? OR conversation_id = ?",
                        UUID.class, conversationId, effectiveParentId);

                // --- 分支：深度思考模式 (Agentic RAG) ---
                if (useDeepThinking) {
                    handleDeepThinking(conversationId, userMessageContent, associatedDocumentIds, latestMessages, conversation, sink);
                    return;
                }

                // --- 分支：标准 RAG 模式 ---
                List<ChatMessage> chronologicalHistory = null;
                if (maxRewriteContext > 0 && !associatedDocumentIds.isEmpty() && !latestMessages.isEmpty()) {
                    chronologicalHistory = latestMessages.stream()
                            .limit(maxRewriteContext)
                            .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                            .collect(Collectors.toList());
                }

                List<String> relevantTextSegments = new ArrayList<>();
                List<Map<String, Object>> sources = new ArrayList<>();
                String finalRewrittenQuery = null;

                if (!associatedDocumentIds.isEmpty()) {
                    String searchKeyword = userMessageContent;

                    // 1. 查询重写：基于历史上下文优化检索词
                    if (maxRewriteContext > 0) {
                        List<ChatMessage> historyToPass = chronologicalHistory != null ? chronologicalHistory : new ArrayList<>();
                        searchKeyword = queryRewriteService.rewriteIfNecessary(userMessageContent, historyToPass);
                    }
                    finalRewrittenQuery = searchKeyword;

                    // 2. 查询分解：将复杂问题拆解为多个子查询
                    List<String> subQueries = queryDecompositionService.decompose(searchKeyword);

                    // 3. 批量混合搜索：执行并汇总所有子查询的检索结果
                    List<Chunk> nearestChunks = retrievalService.batchHybridSearch(subQueries, associatedDocumentIds);

                    for (Chunk chunk : nearestChunks) {
                        relevantTextSegments.add(chunk.getContent());
                        sources.add(extractSourceInfo(chunk));
                    }

                    // 发送引用源事件给前端
                    if (!sources.isEmpty()) {
                        sink.next(ServerSentEvent.<String>builder()
                                .event("sources")
                                .data(objectMapper.writeValueAsString(sources))
                                .build());
                    }
                }

                final String sourcesJsonToSave = (!sources.isEmpty()) ? convertToJsonSilently(objectMapper, sources) : null;
                final String capturedRewrittenQuery = finalRewrittenQuery;

                // 获取标准 RAG 系统提示词
                String standardRagSystemPrompt = promptService.getPrompt("standard_rag", """
                        你是一个专业的智能助手，专门负责基于提供的上下文信息回答用户问题。

                        ## 核心原则
                        1. **基于事实回答**：严格基于提供的上下文信息回答问题，不添加外部知识
                        2. **准确引用**：在回答中明确引用上下文中的相关信息和来源
                        3. **信息完整性**：如果上下文信息不足以完全回答问题，请明确指出信息不足的部分
                        4. **逻辑清晰**：回答结构清晰，逻辑连贯，便于理解

                        ## 回答要求
                        - 使用简洁明了的中文表达
                        - 优先使用上下文中的专业术语和概念
                        - 如果涉及多个相关信息点，请进行适当归纳整理
                        - 保持客观中立的态度，避免主观判断

                        ## 特殊情况处理
                        - 如果问题与上下文完全无关，礼貌说明无法基于提供的信息回答
                        - 如果上下文包含矛盾信息，指出存在差异并解释可能原因
                        - 对于需要计算或推导的问题，如果上下文提供足够数据则进行，否则说明数据不足

                        请始终记住：你的回答必须完全基于提供的上下文信息，不能依赖预训练知识或外部信息。
                        """);

                // 构建对话上下文
                List<dev.langchain4j.data.message.ChatMessage> messages = buildPromptMessages(latestMessages, relevantTextSegments, userMessageContent, standardRagSystemPrompt);

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
                        handleChatCompletion(conversationId, userMessageContent, capturedRewrittenQuery, sourcesJsonToSave, traceId, response, conversation, sink);
                    }
                    @Override
                    public void onError(Throwable error) {
                        sink.error(error);
                    }
                });

            } catch (Exception e) {
                log.error("streamChat 处理失败", e);
                sink.next(ServerSentEvent.<String>builder()
                        .event("error")
                        .data("处理您的请求时遇到错误: " + e.getMessage())
                        .build());
                sink.error(e);
            } finally {
                TraceContext.clear();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void handleDeepThinking(Long conversationId, String userMessageContent, List<UUID> associatedDocumentIds,
                                    List<ChatMessage> latestMessages, Conversation conversation, FluxSink<ServerSentEvent<String>> sink) throws Exception {
        log.info("启动深度思考模式处理会话: {}", conversationId);
        try {
            List<dev.langchain4j.data.message.ChatMessage> agentMessages = new ArrayList<>();

            // 动态加载系统提示词
            String systemPrompt = promptService.getPrompt("deep_thinking_agent", """
                    你是一个具备深度思考能力的 RAG 智能助手。你的目标是利用可用工具准确回答用户的问题。

                    请遵循以下思考流程 (ReAct)：
                    1. **Analyze (分析)**: 仔细分析用户的问题，判断是否需要拆解复杂问题或直接搜索。
                    2. **Act (行动)**: 根据分析结果，选择合适的工具（searchKnowledgeBase 或 decomposeQuery）。
                    3. **Observe (观察)**: 观察工具返回的结果。
                    4. **Reason (推理)**: 基于观察到的信息，判断是否足够回答用户问题。如果不够，决定下一步行动（如根据新线索再次搜索）。
                       - 如果拆解了问题，请依次搜索每个子问题。
                       - 如果搜索结果不理想，尝试使用不同的关键词重试。
                    5. **Reply (回答)**: 当收集到足够信息后，综合整理并给出最终答案。

                    注意：
                    - 优先使用事实数据回答。
                    - 严禁在搜索关键词中编造具体的年份，除非用户问题中明确包含。如果不确定时间，请使用"最新"或不带时间限制的关键词。
                    - 如果多次搜索仍未找到答案，请诚实告知用户。
                    - 最终回答要条理清晰，引用检索到的信息。
                    """);
            agentMessages.add(SystemMessage.from(systemPrompt));

            latestMessages.stream()
                    .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                    .forEach(msg -> {
                        if ("USER".equals(msg.getRole())) agentMessages.add(UserMessage.from(msg.getContent()));
                        else if ("ASSISTANT".equals(msg.getRole())) agentMessages.add(AiMessage.from(msg.getContent()));
                    });
            agentMessages.add(UserMessage.from(userMessageContent));

            // 构建请求级工具实例，注入 Sink
            RagAgentTools requestScopedTools = new RagAgentTools(retrievalService, queryDecompositionService, sink, associatedDocumentIds);
            
            // 构建请求级 Agent 实例
            DeepThinkingAgent requestScopedAgent = AiServices.builder(DeepThinkingAgent.class)
                    .streamingChatModel(openAiStreamingChatModel)
                    .tools(requestScopedTools)
                    .build();
            
            Flux<String> tokenFlux = requestScopedAgent.chat(agentMessages);
            StringBuilder fullAnswer = new StringBuilder();

            tokenFlux.subscribe(
                token -> {
                    fullAnswer.append(token);
                    sink.next(ServerSentEvent.<String>builder()
                            .event("message")
                            .data(token)
                            .build());
                },
                error -> {
                    log.error("深度思考模式流处理失败", error);
                    sink.next(ServerSentEvent.<String>builder()
                            .event("message")
                            .data("抱歉，深度思考模式遇到问题: " + error.getMessage())
                            .build());
                    sink.error(error);
                },
                () -> {
                    String finalAnswer = fullAnswer.toString();
                    saveAiMessage(conversationId, finalAnswer, null);
                    updateConversationTime(conversation);
                    sink.complete();
                }
            );

        } catch (Exception e) {
            log.error("深度思考模式启动失败", e);
            sink.error(e);
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

    private List<dev.langchain4j.data.message.ChatMessage> buildPromptMessages(List<ChatMessage> latestMessages, List<String> relevantTextSegments, String userMessageContent, String systemPrompt) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        latestMessages.stream()
                .limit(MAX_CONTEXT_MESSAGES)
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .forEach(msg -> {
                    if ("USER".equals(msg.getRole())) messages.add(new UserMessage(msg.getContent()));
                    else if ("ASSISTANT".equals(msg.getRole())) messages.add(new AiMessage(msg.getContent()));
                });

        messages.addFirst(SystemMessage.systemMessage(systemPrompt));
        String context = relevantTextSegments.isEmpty() ? "" : "Context:\n" + String.join("\n---\n", relevantTextSegments);
        messages.add(new UserMessage(context + "\n\nQuestion: " + userMessageContent));
        return messages;
    }

    private void handleChatCompletion(Long conversationId, String userQuery, String rewrittenQuery, String sourcesJson, 
                                      String traceId, ChatResponse response, Conversation conversation, FluxSink<ServerSentEvent<String>> sink) {
        try {
            String aiResponseText = response.aiMessage().text();
            saveAiMessage(conversationId, aiResponseText, sourcesJson);
            saveRagInteraction(traceId, conversationId, conversation.getUserId(), userQuery, rewrittenQuery, aiResponseText, sourcesJson);
            updateConversationTime(conversation);
            sink.complete();
        } catch (Exception e) {
            log.error("保存 RAG 交互日志失败", e);
            sink.error(e);
        }
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

    private void saveRagInteraction(String traceId, Long conversationId, Long userId, String userQuery, String rewrittenQuery, String aiResponse, String sourcesJson) {
        String insertSql = """
            INSERT INTO rag_interactions 
            (trace_id, conversation_id, user_id, user_query, rewritten_query, ai_response, retrieved_contexts, created_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """;
        jdbcTemplate.update(insertSql, traceId, conversationId, userId, userQuery, rewrittenQuery, aiResponse, sourcesJson, LocalDateTime.now());
    }

    private void updateConversationTime(Conversation conversation) {
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    private String convertToJsonSilently(ObjectMapper mapper, Object obj) {
        try { return mapper.writeValueAsString(obj); } catch (Exception e) { return null; }
    }
}