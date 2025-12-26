package com.example.qarag.service;

import com.example.qarag.api.config.Observed;
import com.example.qarag.config.RagProperties;
import com.example.qarag.config.TraceContext;
import com.example.qarag.domain.Chunk;
import com.example.qarag.domain.ChatMessage;
import com.example.qarag.domain.Conversation;
import com.example.qarag.domain.Document;
import com.example.qarag.qa.QAService;
import com.example.qarag.repository.ChatMessageRepository;
import com.example.qarag.repository.ConversationRepository;
import com.example.qarag.repository.DocumentRepository;
import com.example.qarag.repository.UserRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int MAX_CONTEXT_MESSAGES = 10;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final StreamingChatModel openAiStreamingChatModel;
    private final JdbcTemplate jdbcTemplate;
    private final QAService qaService;
    private final QueryRewriteService queryRewriteService;
    private final QueryDecompositionService queryDecompositionService;
    private final LangFuseService langFuseService;
    private final RagProperties ragProperties;

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(user -> "ADMIN".equals(user.getRole()))
                .orElse(false);
    }

    private Long checkMessageAccess(Long messageId, Long userId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("未找到消息"));
        Long conversationId = message.getConversationId();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));
        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权访问该消息");
        }
        return conversationId;
    }

    @Transactional
    public Conversation createConversation(Long userId, String title, List<UUID> documentIds, boolean isPublic, Long parentId, String allowedUsers) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setPublic(isPublic);
        conversation.setParentId(parentId);
        conversation.setAllowedUsers(allowedUsers);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        Conversation savedConversation = conversationRepository.save(conversation);

        if (documentIds != null && !documentIds.isEmpty()) {
            String sql = "INSERT INTO conversation_documents (conversation_id, document_id) VALUES (?, ?)";
            List<Object[]> batchArgs = new ArrayList<>();
            for (UUID docId : documentIds) {
                batchArgs.add(new Object[]{savedConversation.getId(), docId});
            }
            jdbcTemplate.batchUpdate(sql, batchArgs);
        }
        return savedConversation;
    }

    public List<Conversation> getConversationsForUser(Long userId) {
        String username = userRepository.findById(userId)
                .map(com.example.qarag.domain.User::getUsername)
                .orElse("");
        return conversationRepository.findAllVisibleConversations(userId, username);
    }

    public List<ChatMessage> getChatMessagesForConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));
        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权访问该对话");
        }
        return chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findConversationById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));
        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权访问该对话");
        }
        jdbcTemplate.update("DELETE FROM conversation_documents WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM chat_messages WHERE conversation_id = ?", conversationId);
        conversationRepository.delete(conversation);
    }

    @Transactional
    public void clearConversationMessages(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findConversationById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));
        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权访问该对话");
        }
        chatMessageRepository.deleteByConversationId(conversationId);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    @Transactional
    public void deleteMessage(Long messageId, Long userId) {
        Long conversationId = checkMessageAccess(messageId, userId);
        chatMessageRepository.deleteById(messageId);
        Conversation conversation = conversationRepository.findConversationById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    @Transactional
    public void addDocumentsToConversation(Long conversationId, Long userId, List<UUID> documentIds) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));
        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权访问该对话");
        }
        if (documentIds == null || documentIds.isEmpty()) return;
        List<Document> documents = (List<Document>) documentRepository.findAllById(documentIds);
        if (documents.size() != documentIds.size()) throw new IllegalArgumentException("未找到一个或多个文档");
        for (Document doc : documents) {
             if (doc.getUserId() != null && !doc.getUserId().equals(userId)) throw new SecurityException("无权访问文档：" + doc.getId());
        }
        String sql = "INSERT INTO conversation_documents (conversation_id, document_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
        List<Object[]> batchArgs = new ArrayList<>();
        for (UUID docId : documentIds) batchArgs.add(new Object[]{conversationId, docId});
        jdbcTemplate.batchUpdate(sql, batchArgs);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    @Transactional
    public void removeDocumentFromConversation(Long conversationId, Long userId, UUID documentId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));
        if (!conversation.getUserId().equals(userId)) throw new SecurityException("无权访问该对话");
        jdbcTemplate.update("DELETE FROM conversation_documents WHERE conversation_id = ? AND document_id = ?", conversationId, documentId);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    public List<Document> getDocumentsForConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));
        if (!conversation.getUserId().equals(userId)) throw new SecurityException("无权访问该对话");
        Long parentId = conversation.getParentId();
        Long effectiveParentId = parentId != null ? parentId : conversationId;
        List<UUID> docIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT document_id FROM conversation_documents WHERE conversation_id = ? OR conversation_id = ?",
                UUID.class, conversationId, effectiveParentId);
        if (docIds.isEmpty()) return new ArrayList<>();
        return (List<Document>) documentRepository.findAllById(docIds);
    }

    @Transactional
    @Observed(name = "Chat Interaction",includeInputFields = {"conversationId","userId","userMessageContent"})
    public void streamChat(Long conversationId, Long userId, String userMessageContent, SseEmitter emitter) {
        // Retrieve the Trace ID generated by the Aspect
        String traceId = TraceContext.getTraceId();

        try {
            // Register Trace with LangFuse (using metadata)
            langFuseService.createTrace(traceId, "Chat Interaction", userId.toString(), Map.of("conversationId", conversationId));

            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("未找到对话"));

            if (!conversation.getUserId().equals(userId)) {
                throw new SecurityException("无权访问该对话");
            }

            int maxRewriteContext = (ragProperties.retrieval().rewrite() != null && ragProperties.retrieval().rewrite().enabled())
                    ? ragProperties.retrieval().rewrite().maxContextMessages()
                    : 0;
            int maxHistoryLimit = Math.max(MAX_CONTEXT_MESSAGES, maxRewriteContext);
            List<ChatMessage> latestMessages = chatMessageRepository.findLatestMessagesByConversationId(conversationId, maxHistoryLimit);

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
                ChatMessage userChatMessage = new ChatMessage();
                userChatMessage.setConversationId(conversationId);
                userChatMessage.setRole("USER");
                userChatMessage.setContent(userMessageContent);
                userChatMessage.setCreatedAt(LocalDateTime.now());
                chatMessageRepository.save(userChatMessage);
            }

            //查询关联文档
            Long parentId = conversation.getParentId();
            Long effectiveParentId = parentId != null ? parentId : conversationId;
            List<UUID> associatedDocumentIds = jdbcTemplate.queryForList(
                    "SELECT DISTINCT document_id FROM conversation_documents WHERE conversation_id = ? OR conversation_id = ?",
                    UUID.class, conversationId, effectiveParentId);


            List<ChatMessage> chronologicalHistory = null;
            if (maxRewriteContext > 0 && !associatedDocumentIds.isEmpty() && !latestMessages.isEmpty()) {
                chronologicalHistory = latestMessages.stream()
                        .limit(maxRewriteContext)
                        .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                        .collect(Collectors.toList());
            }

            List<String> relevantTextSegments = new ArrayList<>();
            if (!associatedDocumentIds.isEmpty()) {
                String searchKeyword = userMessageContent;

                // 1. Rewrite
                // @Observed handles monitoring
                if (maxRewriteContext > 0) {
                    List<ChatMessage> historyToPass = chronologicalHistory != null ? chronologicalHistory : new ArrayList<>();
                    searchKeyword = queryRewriteService.rewriteIfNecessary(userMessageContent, historyToPass);
                }

                // 2. Decompose
                // @Observed handles monitoring
                List<String> subQueries = queryDecompositionService.decompose(searchKeyword);

                // 3. Batch Hybrid Search
                // @Observed handles monitoring
                List<Chunk> nearestChunks = qaService.batchHybridSearch(subQueries, associatedDocumentIds);

                for (Chunk chunk : nearestChunks) relevantTextSegments.add(chunk.getContent());
            }

            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
            latestMessages.stream()
                    .limit(MAX_CONTEXT_MESSAGES)
                    .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                    .map(msg -> {
                        if ("USER".equals(msg.getRole())) return new UserMessage(msg.getContent());
                        else if ("ASSISTANT".equals(msg.getRole())) return new AiMessage(msg.getContent());
                        else return null;
                    })
                    .filter(java.util.Objects::nonNull)
                    .forEach(messages::add);

            messages.addFirst(SystemMessage.systemMessage("You are a helpful assistant. Answer questions based on the provided context."));
            String context = relevantTextSegments.isEmpty() ? "" : "Context:\n" + String.join("\n---\n", relevantTextSegments);
            messages.add(new UserMessage(context + "\n\nQuestion: " + userMessageContent));

            TraceContext.setNextGenerationName("LLM: Final Generation");
            openAiStreamingChatModel.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    try {
                        emitter.send(token, org.springframework.http.MediaType.TEXT_PLAIN);
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                }
                @Override
                public void onCompleteResponse(ChatResponse response) {
                    ChatMessage aiChatMessage = new ChatMessage();
                    aiChatMessage.setConversationId(conversationId);
                    aiChatMessage.setRole("ASSISTANT");
                    aiChatMessage.setContent(response.aiMessage().text());
                    aiChatMessage.setCreatedAt(LocalDateTime.now());
                    chatMessageRepository.save(aiChatMessage);
                    conversation.setUpdatedAt(LocalDateTime.now());
                    conversationRepository.save(conversation);
                    emitter.complete();
                }
                @Override
                public void onError(Throwable error) {
                    emitter.completeWithError(error);
                }
            });

        } catch (Exception e) {
            log.error("Error in streamChat", e);
            emitter.completeWithError(e);
            throw e; // Re-throw so Aspect can record the error
        } finally {
            TraceContext.clear();
        }
    }

    @Transactional
    public void toggleConversationPublicStatus(Long conversationId, boolean isPublic, String allowedUsers, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));
        if (!isAdmin(userId)) throw new SecurityException("只有管理员可以管理公共助手");
        if (conversation.getParentId() != null && isPublic) throw new IllegalArgumentException("子对话不能直接设为公开。");
        conversation.setPublic(isPublic);
        conversation.setAllowedUsers(allowedUsers);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }
}
