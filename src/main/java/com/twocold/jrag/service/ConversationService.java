package com.twocold.jrag.service;

import com.twocold.jrag.domain.ChatMessage;
import com.twocold.jrag.domain.Conversation;
import com.twocold.jrag.domain.Document;
import com.twocold.jrag.repository.ChatMessageRepository;
import com.twocold.jrag.repository.ConversationRepository;
import com.twocold.jrag.repository.DocumentRepository;
import com.twocold.jrag.repository.TemplateDocumentRepository;
import com.twocold.jrag.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final TemplateRepository templateRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TemplateDocumentRepository templateDocumentRepository;
    private final UserService userService;

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
    public Conversation createConversation(Long userId, String title, List<UUID> documentIds, boolean isPublic, Long parentId, String allowedUsers, Long templateId) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setPublic(isPublic);
        conversation.setParentId(parentId);
        conversation.setAllowedUsers(allowedUsers);
        conversation.setTemplateId(templateId); // Set templateId
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        Conversation savedConversation = conversationRepository.save(conversation);

        Set<UUID> finalDocumentIds = new HashSet<>();
        if (documentIds != null) {
            finalDocumentIds.addAll(documentIds);
        }
        
        // If templateId is provided, add documents from template
        if (templateId != null) {
            if (!templateRepository.existsById(templateId)) {
                throw new IllegalArgumentException("未找到模板: " + templateId);
            }
            List<com.twocold.jrag.domain.TemplateDocument> templateDocs = templateDocumentRepository.findByTemplateId(templateId);
            for (com.twocold.jrag.domain.TemplateDocument td : templateDocs) {
                finalDocumentIds.add(td.getDocumentId());
            }
        }

        if (!finalDocumentIds.isEmpty()) {
            String sql = "INSERT INTO conversation_documents (conversation_id, document_id) VALUES (?, ?)";
            List<Object[]> batchArgs = new ArrayList<>();
            for (UUID docId : finalDocumentIds) {
                batchArgs.add(new Object[]{savedConversation.getId(), docId});
            }
            jdbcTemplate.batchUpdate(sql, batchArgs);
        }
        return savedConversation;
    }

    public List<Conversation> getConversationsForUser(Long userId) {
        return conversationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId);
    }

    // 获取所有公开的对话（团队对话）
    public List<Conversation> getPublicConversations() {
        return conversationRepository.findAllByIsPublicTrueOrderByUpdatedAtDesc();
    }

    // 获取公开对话的消息（不需要用户验证）
    public List<ChatMessage> getPublicChatMessages(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));
        if (!conversation.isPublic()) {
            throw new SecurityException("该对话不是公开的");
        }
        return chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId);
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
    public void toggleConversationPublicStatus(Long conversationId, boolean isPublic, String allowedUsers, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对话"));
        if (!userService.isAdmin(userId)) throw new SecurityException("只有管理员可以管理公共助手");
        if (conversation.getParentId() != null && isPublic) throw new IllegalArgumentException("子对话不能直接设为公开。");
        conversation.setPublic(isPublic);
        conversation.setAllowedUsers(allowedUsers);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }
}