package com.example.qarag.service;

import com.example.qarag.config.RagProperties;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import dev.langchain4j.data.message.TextContent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int MAX_CONTEXT_MESSAGES = 10;
    private static final int TOP_K_CHUNKS = 5;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final StreamingChatModel openAiStreamingChatModel;
    private final JdbcTemplate jdbcTemplate;
    private final QAService qaService;
    private final QueryRewriteService queryRewriteService;
    private final RagProperties ragProperties;

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(user -> "ADMIN".equals(user.getRole()))
                .orElse(false);
    }

    /**
     * 检查用户是否有权访问聊天消息
     * @param messageId 消息 ID
     * @param userId 用户 ID
     * @return 对话 ID
     * @throws SecurityException 如果拒绝访问
     */
    private Long checkMessageAccess(Long messageId, Long userId) {
        // 首先获取消息
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("未找到消息"));

        Long conversationId = message.getConversationId();

        // 检查用户是否有权访问该对话
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
        conversation.setPublic(isPublic); // 设置是否公开
        conversation.setParentId(parentId); // 设置父级 ID
        conversation.setAllowedUsers(allowedUsers); // 设置允许访问的用户
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

        // 1. 删除 conversation_documents 中的关联关系
        jdbcTemplate.update("DELETE FROM conversation_documents WHERE conversation_id = ?", conversationId);

        // 2. 删除聊天消息
        jdbcTemplate.update("DELETE FROM chat_messages WHERE conversation_id = ?", conversationId);

        // 3. 删除对话本身
        conversationRepository.delete(conversation);
    }

    /**
     * 清除对话中的所有消息
     * @param conversationId 对话 ID
     * @param userId 用户 ID
     */
    @Transactional
    public void clearConversationMessages(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findConversationById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));

        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权访问该对话");
        }

        // 删除该对话的所有消息
        chatMessageRepository.deleteByConversationId(conversationId);

        // 更新对话时间戳
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    /**
     * 删除单条消息
     * @param messageId 消息 ID
     * @param userId 用户 ID
     */
    @Transactional
    public void deleteMessage(Long messageId, Long userId) {
        // 检查访问权限并获取对话 ID
        Long conversationId = checkMessageAccess(messageId, userId);

        // 删除消息
        chatMessageRepository.deleteById(messageId);

        // 更新对话时间戳
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

        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }

        List<Document> documents = (List<Document>) documentRepository.findAllById(documentIds);
        if (documents.size() != documentIds.size()) {
             throw new IllegalArgumentException("未找到一个或多个文档");
        }
        for (Document doc : documents) {
             if (doc.getUserId() != null && !doc.getUserId().equals(userId)) {
                 throw new SecurityException("无权访问文档：" + doc.getId());
             }
        }

        String sql = "INSERT INTO conversation_documents (conversation_id, document_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
        List<Object[]> batchArgs = new ArrayList<>();
        for (UUID docId : documentIds) {
            batchArgs.add(new Object[]{conversationId, docId});
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
        
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    @Transactional
    public void removeDocumentFromConversation(Long conversationId, Long userId, UUID documentId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));

        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权访问该对话");
        }

        jdbcTemplate.update("DELETE FROM conversation_documents WHERE conversation_id = ? AND document_id = ?", conversationId, documentId);
        
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    public List<Document> getDocumentsForConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));

        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权访问该对话");
        }

        // 检查父对话以继承文档。从当前对话和父对话中同时检索。
        Long parentId = conversation.getParentId();
        Long effectiveParentId = parentId != null ? parentId : conversationId;

        List<UUID> docIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT document_id FROM conversation_documents WHERE conversation_id = ? OR conversation_id = ?",
                UUID.class,
                conversationId,
                effectiveParentId
        );
        
        if (docIds.isEmpty()) {
            return new ArrayList<>();
        }

        return (List<Document>) documentRepository.findAllById(docIds);
    }

    @Transactional
    public void streamChat(Long conversationId, Long userId, String userMessageContent, SseEmitter emitter) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));

        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权访问该对话");
        }

        // 一次性获取对话历史，用于幂等性检查、重写和 LLM 上下文
        int maxRewriteContext = (ragProperties.retrieval().rewrite() != null && ragProperties.retrieval().rewrite().enabled())
                ? ragProperties.retrieval().rewrite().maxContextMessages()
                : 0;
        int maxHistoryLimit = Math.max(MAX_CONTEXT_MESSAGES, maxRewriteContext);
        List<ChatMessage> latestMessages = chatMessageRepository.findLatestMessagesByConversationId(conversationId, maxHistoryLimit);

        // 幂等性检查：防止重试时出现重复的用户消息
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
            log.warn("Duplicate user message detected (ConversationId: {}): {}", conversationId, userMessageContent);
            
            // 检查该消息后是否已有 AI 响应
            // latestMessages 按 created_at DESC 排序（最新的在前）
            int index = latestMessages.indexOf(existingUserMsg);
            if (index > 0) {
                ChatMessage potentialAiReply = latestMessages.get(index - 1);
                if ("ASSISTANT".equals(potentialAiReply.getRole())) {
                    log.info("Found existing AI response, replaying content...");
                    try {
                        // 立即回放完整内容
                        emitter.send(potentialAiReply.getContent(), org.springframework.http.MediaType.TEXT_PLAIN);
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                    return; // 停止进一步处理
                }
            }
            // 如果未找到 AI 响应，我们继续处理（例如，之前的尝试失败或仍在运行）
            // 但我们不会再次保存用户消息。
        } else {
            // 1. 立即保存用户消息
            ChatMessage userChatMessage = new ChatMessage();
            userChatMessage.setConversationId(conversationId);
            userChatMessage.setRole("USER");
            userChatMessage.setContent(userMessageContent);
            userChatMessage.setCreatedAt(LocalDateTime.now());
            chatMessageRepository.save(userChatMessage);
        }

        // 2. 准备上下文（检索数据块）
        // 检查父对话以继承文档。从当前对话和父对话中同时检索。
        Long parentId = conversation.getParentId();
        Long effectiveParentId = parentId != null ? parentId : conversationId;

        List<UUID> associatedDocumentIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT document_id FROM conversation_documents WHERE conversation_id = ? OR conversation_id = ?",
                UUID.class,
                conversationId,
                effectiveParentId
        );

        // 如果需要，准备用于重写的按时间顺序排列的历史记录
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
            
            // 如果启用且存在历史记录，执行上下文查询重写
            if (chronologicalHistory != null && !chronologicalHistory.isEmpty()) {
                searchKeyword = queryRewriteService.rewriteIfNecessary(userMessageContent, chronologicalHistory);
            }

            List<Chunk> nearestChunks = qaService.hybridSearch(
                    searchKeyword,
                    associatedDocumentIds
            );

            log.info("RAG Search Results (ConversationId: {}): Found {} chunks.", conversationId, nearestChunks.size());
            for (int i = 0; i < nearestChunks.size(); i++) {
                Chunk chunk = nearestChunks.get(i);
                relevantTextSegments.add(chunk.getContent());
                
                // 记录数据块详情（将内容预览限制为 100 个字符以避免日志泛滥）
                String contentPreview = chunk.getContent().length() > 100 
                        ? chunk.getContent().substring(0, 100) + "..." 
                        : chunk.getContent();
                log.info("  [Chunk {}] DocID: {}, Content: {}", i, chunk.getDocumentId(), contentPreview.replace("\n", " "));
            }
        }

        // 3. 使用缓存的历史记录准备消息
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        latestMessages.stream()
                .limit(MAX_CONTEXT_MESSAGES)
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(msg -> {
                    if ("USER".equals(msg.getRole())) {
                        return new UserMessage(msg.getContent());
                    } else if ("ASSISTANT".equals(msg.getRole())) {
                        return new AiMessage(msg.getContent());
                    } else {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .forEach(messages::add);

        messages.addFirst(SystemMessage.systemMessage("You are a helpful assistant. Answer questions based on the provided context."));
        
        String context = relevantTextSegments.isEmpty() ?
                "" : "Context:\n" + String.join("\n---\n", relevantTextSegments);
        messages.add(new UserMessage(context + "\n\nQuestion: " + userMessageContent));

        // 记录发送给 LLM 的完整提示词
        log.info("Sending request to LLM (ConversationId: {}). Total Messages: {}", conversationId, messages.size());
        for (int i = 0; i < messages.size(); i++) {
            dev.langchain4j.data.message.ChatMessage msg = messages.get(i);
            // 限制超长消息的日志长度（例如带有上下文的消息）
            String text;
            switch (msg) {
                case UserMessage userMsg -> {
                    if (userMsg.hasSingleText()) {
                        text = userMsg.singleText();
                    } else {
                        // 多模态消息的备选方案，或记录警告
                        text = userMsg.contents().stream()
                                .filter(content -> content instanceof TextContent)
                                .map(content -> ((TextContent) content).text())
                                .collect(Collectors.joining(" "));
                    }
                }
                case AiMessage aiMessage -> text = aiMessage.text();
                case SystemMessage systemMessage -> text = systemMessage.text();
                case null, default -> text = msg.toString();
            }
            if (text.length() > 500) {
                text = text.substring(0, 500) + "... [truncated " + (text.length() - 500) + " chars]";
            }
            log.info("  [Msg {} - {}] {}", i, msg.type(), text);
        }

        // 4. 流式响应
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
                log.info("LLM Final Response (ConversationId: {}): \n{}", conversationId, response.aiMessage().text());
                // 完成后将 AI 消息保存到数据库
                ChatMessage aiChatMessage = new ChatMessage();
                aiChatMessage.setConversationId(conversationId);
                aiChatMessage.setRole("ASSISTANT");
                aiChatMessage.setContent(response.aiMessage().text());
                aiChatMessage.setCreatedAt(LocalDateTime.now());
                chatMessageRepository.save(aiChatMessage);

                // 更新对话时间戳
                conversation.setUpdatedAt(LocalDateTime.now());
                conversationRepository.save(conversation);
                
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                emitter.completeWithError(error);
            }
        });
    }

    @Transactional
    public void toggleConversationPublicStatus(Long conversationId, boolean isPublic, String allowedUsers, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));

        if (!isAdmin(userId)) {
            throw new SecurityException("只有管理员可以管理公共助手");
        }
        
        // 只有非子对话可以设为公开。子对话始终遵循其父对话。
        if (conversation.getParentId() != null && isPublic) {
            throw new IllegalArgumentException("子对话不能直接设为公开。");
        }

        conversation.setPublic(isPublic);
        conversation.setAllowedUsers(allowedUsers); // 更新白名单
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }
}
