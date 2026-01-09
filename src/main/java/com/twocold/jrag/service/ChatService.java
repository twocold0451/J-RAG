package com.twocold.jrag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twocold.jrag.agent.AgentContext;
import com.twocold.jrag.agent.DeepThinkingAgent;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private final DeepThinkingAgent deepThinkingAgent;
    private final ObjectMapper objectMapper;

    /**
     * 执行流式 RAG 对话
     * 
     * @param conversationId 对话 ID
     * @param userId 用户 ID
     * @param userMessageContent 用户提问内容
     * @param useDeepThinking 是否启用深度思考（Agent）模式
     * @param emitter SSE 发送器
     */
    @Transactional
    @Observed(name = "Chat Interaction", includeInputFields = {"conversationId", "userId", "userMessageContent"})
    public void streamChat(Long conversationId, Long userId, String userMessageContent, boolean useDeepThinking, SseEmitter emitter) {
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
                        try {
                            emitter.send(potentialAiReply.getContent(), org.springframework.http.MediaType.TEXT_PLAIN);
                            emitter.complete();
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
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
                handleDeepThinking(conversationId, userMessageContent, associatedDocumentIds, latestMessages, conversation, emitter);
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
                    emitter.send(SseEmitter.event().name("sources").data(objectMapper.writeValueAsString(sources)));
                }
            }

            final String sourcesJsonToSave = (!sources.isEmpty()) ? convertToJsonSilently(objectMapper, sources) : null;
            final String capturedRewrittenQuery = finalRewrittenQuery;

            // 构建对话上下文
            List<dev.langchain4j.data.message.ChatMessage> messages = buildPromptMessages(latestMessages, relevantTextSegments, userMessageContent);

            TraceContext.setNextGenerationName("LLM: Final Generation");
            openAiStreamingChatModel.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(token));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                }
                @Override
                public void onCompleteResponse(ChatResponse response) {
                    handleChatCompletion(conversationId, userMessageContent, capturedRewrittenQuery, sourcesJsonToSave, traceId, response, conversation, emitter);
                }
                @Override
                public void onError(Throwable error) {
                    emitter.completeWithError(error);
                }
            });

        } catch (Exception e) {
            log.error("streamChat 处理失败", e);
            emitter.completeWithError(e);
        } finally {
            TraceContext.clear();
        }
    }

    private void handleDeepThinking(Long conversationId, String userMessageContent, List<UUID> associatedDocumentIds, 
                                    List<ChatMessage> latestMessages, Conversation conversation, SseEmitter emitter) throws Exception {
        log.info("启动深度思考模式处理会话: {}", conversationId);
        try {
            List<dev.langchain4j.data.message.ChatMessage> agentMessages = new ArrayList<>();
            latestMessages.stream()
                    .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                    .forEach(msg -> {
                        if ("USER".equals(msg.getRole())) agentMessages.add(UserMessage.from(msg.getContent()));
                        else if ("ASSISTANT".equals(msg.getRole())) agentMessages.add(AiMessage.from(msg.getContent()));
                    });
            agentMessages.add(UserMessage.from(userMessageContent));

            AgentContext.setDocumentIds(associatedDocumentIds);
            String answer = deepThinkingAgent.chat(agentMessages);

            emitter.send(SseEmitter.event().name("delta").data(answer));

            // 保存 AI 响应
            saveAiMessage(conversationId, answer, null);
            updateConversationTime(conversation);
            emitter.complete();
        } catch (Exception e) {
            log.error("深度思考模式处理失败", e);
            emitter.send(SseEmitter.event().name("delta").data("抱歉，深度思考模式遇到问题: " + e.getMessage()));
            emitter.completeWithError(e);
        } finally {
            AgentContext.clear();
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

    private List<dev.langchain4j.data.message.ChatMessage> buildPromptMessages(List<ChatMessage> latestMessages, List<String> relevantTextSegments, String userMessageContent) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        latestMessages.stream()
                .limit(MAX_CONTEXT_MESSAGES)
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .forEach(msg -> {
                    if ("USER".equals(msg.getRole())) messages.add(new UserMessage(msg.getContent()));
                    else if ("ASSISTANT".equals(msg.getRole())) messages.add(new AiMessage(msg.getContent()));
                });

        messages.addFirst(SystemMessage.systemMessage("You are a helpful assistant. Answer questions based on the provided context."));
        String context = relevantTextSegments.isEmpty() ? "" : "Context:\n" + String.join("\n---\n", relevantTextSegments);
        messages.add(new UserMessage(context + "\n\nQuestion: " + userMessageContent));
        return messages;
    }

    private void handleChatCompletion(Long conversationId, String userQuery, String rewrittenQuery, String sourcesJson, 
                                      String traceId, ChatResponse response, Conversation conversation, SseEmitter emitter) {
        try {
            String aiResponseText = response.aiMessage().text();
            saveAiMessage(conversationId, aiResponseText, sourcesJson);
            saveRagInteraction(traceId, conversationId, conversation.getUserId(), userQuery, rewrittenQuery, aiResponseText, sourcesJson);
            updateConversationTime(conversation);
            emitter.complete();
        } catch (Exception e) {
            log.error("保存 RAG 交互日志失败", e);
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