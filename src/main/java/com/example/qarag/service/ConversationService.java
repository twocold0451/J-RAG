package com.example.qarag.service;

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

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(user -> "ADMIN".equals(user.getRole()))
                .orElse(false);
    }

    /**
     * Check if a user has access to a chat message
     * @param messageId The message ID
     * @param userId The user ID
     * @return The conversation ID
     * @throws SecurityException if access is denied
     */
    private Long checkMessageAccess(Long messageId, Long userId) {
        // Get the message first
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        Long conversationId = message.getConversationId();

        // Check if user has access to the conversation
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to message");
        }

        return conversationId;
    }

    @Transactional
    public Conversation createConversation(Long userId, String title, List<UUID> documentIds, boolean isPublic, Long parentId, String allowedUsers) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setPublic(isPublic); // Set isPublic
        conversation.setParentId(parentId); // Set parentId
        conversation.setAllowedUsers(allowedUsers); // Set allowedUsers
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
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to conversation");
        }
        return chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findConversationById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to conversation");
        }

        // 1. Delete relations in conversation_documents
        jdbcTemplate.update("DELETE FROM conversation_documents WHERE conversation_id = ?", conversationId);

        // 2. Delete chat messages
        jdbcTemplate.update("DELETE FROM chat_messages WHERE conversation_id = ?", conversationId);

        // 3. Delete conversation itself
        conversationRepository.delete(conversation);
    }

    /**
     * Clear all messages from a conversation
     * @param conversationId The conversation ID
     * @param userId The user ID
     */
    @Transactional
    public void clearConversationMessages(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findConversationById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to conversation");
        }

        // Delete all messages for this conversation
        chatMessageRepository.deleteByConversationId(conversationId);

        // Update conversation timestamp
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    /**
     * Delete a single message
     * @param messageId The message ID
     * @param userId The user ID
     */
    @Transactional
    public void deleteMessage(Long messageId, Long userId) {
        // Check access and get conversation ID
        Long conversationId = checkMessageAccess(messageId, userId);

        // Delete the message
        chatMessageRepository.deleteById(messageId);

        // Update conversation timestamp
        Conversation conversation = conversationRepository.findConversationById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    @Transactional
    public void addDocumentsToConversation(Long conversationId, Long userId, List<UUID> documentIds) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        
        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to conversation");
        }

        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }

        List<Document> documents = (List<Document>) documentRepository.findAllById(documentIds);
        if (documents.size() != documentIds.size()) {
             throw new IllegalArgumentException("One or more documents not found");
        }
        for (Document doc : documents) {
             if (doc.getUserId() != null && !doc.getUserId().equals(userId)) {
                 throw new SecurityException("Access denied to document: " + doc.getId());
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
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to conversation");
        }

        jdbcTemplate.update("DELETE FROM conversation_documents WHERE conversation_id = ? AND document_id = ?", conversationId, documentId);
        
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    public List<Document> getDocumentsForConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to conversation");
        }

        // Check for parent conversation to inherit documents. Retrieve from both current and parent.
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

    // Keep the blocking chat method for backward compatibility if needed, or remove it.
    // We will focus on the streaming version.

    @Transactional
    public void streamChat(Long conversationId, Long userId, String userMessageContent, SseEmitter emitter) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to conversation");
        }

        // Idempotency check: prevent duplicate user messages on retry
        List<ChatMessage> recentMessages = chatMessageRepository.findLatestMessagesByConversationId(conversationId, 10);
        ChatMessage existingUserMsg = null;
        for (ChatMessage msg : recentMessages) {
            if ("USER".equals(msg.getRole())
                    && msg.getContent().equals(userMessageContent)
                    && msg.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(30))) {
                existingUserMsg = msg;
                break;
            }
        }

        if (existingUserMsg != null) {
            log.warn("Duplicate user message detected (ConversationId: {}): {}", conversationId, userMessageContent);
            
            // Check if there is already an AI response following this message
            // recentMessages is ordered by created_at DESC (latest first)
            int index = recentMessages.indexOf(existingUserMsg);
            if (index > 0) {
                ChatMessage potentialAiReply = recentMessages.get(index - 1);
                if ("ASSISTANT".equals(potentialAiReply.getRole())) {
                    log.info("Found existing AI response, replaying content...");
                    try {
                        // Replay the full content immediately
                        emitter.send(potentialAiReply.getContent(), org.springframework.http.MediaType.TEXT_PLAIN);
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                    return; // Stop further processing
                }
            }
            // If no AI response found, we continue processing (e.g. previous attempt failed or is still running)
            // But we DO NOT save the user message again.
        } else {
            // 1. Save user message immediately
            ChatMessage userChatMessage = new ChatMessage();
            userChatMessage.setConversationId(conversationId);
            userChatMessage.setRole("USER");
            userChatMessage.setContent(userMessageContent);
            userChatMessage.setCreatedAt(LocalDateTime.now());
            chatMessageRepository.save(userChatMessage);
        }

        // 2. Prepare Context (Retrieve chunks)
        // Check for parent conversation to inherit documents. Retrieve from both current and parent.
        Long parentId = conversation.getParentId();
        Long effectiveParentId = parentId != null ? parentId : conversationId;

        List<UUID> associatedDocumentIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT document_id FROM conversation_documents WHERE conversation_id = ? OR conversation_id = ?",
                UUID.class,
                conversationId,
                effectiveParentId
        );

        List<String> relevantTextSegments = new ArrayList<>();
        if (!associatedDocumentIds.isEmpty()) {
            List<Chunk> nearestChunks = qaService.hybridSearch(
                    userMessageContent,
                    TOP_K_CHUNKS,
                    associatedDocumentIds
            );

            log.info("RAG Search Results (ConversationId: {}): Found {} chunks.", conversationId, nearestChunks.size());
            for (int i = 0; i < nearestChunks.size(); i++) {
                Chunk chunk = nearestChunks.get(i);
                relevantTextSegments.add(chunk.getContent());
                
                // Log chunk details (limit content preview to 100 chars to avoid log spam)
                String contentPreview = chunk.getContent().length() > 100 
                        ? chunk.getContent().substring(0, 100) + "..." 
                        : chunk.getContent();
                log.info("  [Chunk {}] DocID: {}, Content: {}", i, chunk.getDocumentId(), contentPreview.replace("\n", " "));
            }
        }

        // 3. Prepare Messages
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        chatMessageRepository.findLatestMessagesByConversationId(conversationId, MAX_CONTEXT_MESSAGES)
                .stream()
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
                .forEach(messages::add);

        messages.addFirst(SystemMessage.systemMessage("You are a helpful assistant. Answer questions based on the provided context."));
        
        String context = relevantTextSegments.isEmpty() ?
                "" : "Context:\n" + String.join("\n---\n", relevantTextSegments);
        messages.add(new UserMessage(context + "\n\nQuestion: " + userMessageContent));

        // Log the full prompt sent to LLM
        log.info("Sending request to LLM (ConversationId: {}). Total Messages: {}", conversationId, messages.size());
        for (int i = 0; i < messages.size(); i++) {
            dev.langchain4j.data.message.ChatMessage msg = messages.get(i);
            // Limit log length for very long messages (like the one with context)
            String text;
            switch (msg) {
                case UserMessage userMsg -> {
                    if (userMsg.hasSingleText()) {
                        text = userMsg.singleText();
                    } else {
                        // Fallback for multi-modal messages, or log a warning
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

        // 4. Stream Response
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
                // Save AI message to DB after completion
                ChatMessage aiChatMessage = new ChatMessage();
                aiChatMessage.setConversationId(conversationId);
                aiChatMessage.setRole("ASSISTANT");
                aiChatMessage.setContent(response.aiMessage().text());
                aiChatMessage.setCreatedAt(LocalDateTime.now());
                chatMessageRepository.save(aiChatMessage);

                // Update conversation timestamp
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
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (!isAdmin(userId)) {
            throw new SecurityException("Only administrators can manage public assistants");
        }
        
        // Only non-child conversations can be made public. A child conversation always follows its parent.
        if (conversation.getParentId() != null && isPublic) {
            throw new IllegalArgumentException("Child conversations cannot be made public directly.");
        }

        conversation.setPublic(isPublic);
        conversation.setAllowedUsers(allowedUsers); // Update whitelist
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }
}