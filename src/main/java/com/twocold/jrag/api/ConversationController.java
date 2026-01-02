package com.twocold.jrag.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twocold.jrag.api.dto.*;
import com.twocold.jrag.config.CurrentUser;
import com.twocold.jrag.domain.ChatMessage;
import com.twocold.jrag.domain.Conversation;
import com.twocold.jrag.domain.Document;
import com.twocold.jrag.repository.UserRepository;
import com.twocold.jrag.service.ConversationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public ConversationController(ConversationService conversationService, ObjectMapper objectMapper, UserRepository userRepository) {
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @CurrentUser Long userId,
            @RequestBody ConversationCreateRequest request) {
        Conversation conversation = conversationService.createConversation(
                userId,
                request.getTitle(),
                request.getDocumentIds(),
                request.isPublic(),
                request.getParentId(),
                request.getAllowedUsers(),
                request.getTemplateId()
        );
        return ResponseEntity.ok(convertToResponse(conversation));
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getConversations(@CurrentUser Long userId) {
        List<Conversation> conversations = conversationService.getConversationsForUser(userId);
        return ResponseEntity.ok(conversations.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));
    }

    // 团队对话 - 获取公开对话列表（管理员使用）
    @GetMapping("/public")
    public ResponseEntity<List<ConversationResponse>> getPublicConversations() {
        List<Conversation> conversations = conversationService.getPublicConversations();
        return ResponseEntity.ok(conversations.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));
    }

    // 团队对话 - 获取公开对话的消息
    @GetMapping("/{conversationId}/public/messages")
    public ResponseEntity<List<ChatMessageDto>> getPublicChatMessages(
            @PathVariable Long conversationId) {
        List<ChatMessage> messages = conversationService.getPublicChatMessages(conversationId);
        return ResponseEntity.ok(messages.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList()));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable Long conversationId,
            @CurrentUser Long userId) {
        conversationService.deleteConversation(conversationId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{conversationId}/messages")
    public ResponseEntity<Void> clearConversationMessages(
            @PathVariable Long conversationId,
            @CurrentUser Long userId) {
        conversationService.clearConversationMessages(conversationId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageId,
            @CurrentUser Long userId) {
        conversationService.deleteMessage(messageId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getChatMessages(
            @PathVariable Long conversationId,
            @CurrentUser Long userId) {
        List<ChatMessage> messages = conversationService.getChatMessagesForConversation(conversationId, userId);
        return ResponseEntity.ok(messages.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList()));
    }

    @PostMapping("/{conversationId}/documents")
    public ResponseEntity<Void> addDocuments(
            @PathVariable Long conversationId,
            @CurrentUser Long userId,
            @RequestBody AddDocumentsRequest request) {
        conversationService.addDocumentsToConversation(conversationId, userId, request.getDocumentIds());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{conversationId}/documents/{documentId}")
    public ResponseEntity<Void> removeDocument(
            @PathVariable Long conversationId,
            @PathVariable UUID documentId,
            @CurrentUser Long userId) {
        conversationService.removeDocumentFromConversation(conversationId, userId, documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{conversationId}/documents")
    public ResponseEntity<List<DocumentDto>> getConversationDocuments(
            @PathVariable Long conversationId,
            @CurrentUser Long userId) {
        List<Document> documents = conversationService.getDocumentsForConversation(conversationId, userId);
        return ResponseEntity.ok(documents.stream()
                .map(DocumentDto::from)
                .collect(Collectors.toList()));
    }

    @PostMapping("/{conversationId}/chat/stream")
    public SseEmitter streamChat(
            @PathVariable Long conversationId,
            @CurrentUser Long userId,
            @RequestBody ChatRequest request) {
        // 超时时间设置为 3 分钟 (180000 毫秒)，以适应较长的生成过程
        SseEmitter emitter = new SseEmitter(180000L);
        conversationService.streamChat(conversationId, userId, request.getMessage(), request.isUseDeepThinking(), emitter);
        return emitter;
    }

    private ConversationResponse convertToResponse(Conversation conversation) {
        ConversationResponse response = new ConversationResponse();
        response.setId(conversation.getId());
        response.setUserId(conversation.getUserId());
        response.setTemplateId(conversation.getTemplateId());
        response.setTitle(conversation.getTitle());
        response.setPublic(conversation.isPublic());
        response.setParentId(conversation.getParentId());
        response.setCreatedAt(conversation.getCreatedAt());
        response.setUpdatedAt(conversation.getUpdatedAt());

        // 获取用户名
        userRepository.findById(conversation.getUserId())
                .ifPresent(user -> response.setUsername(user.getUsername()));

        return response;
    }

    private ChatMessageDto convertToDto(ChatMessage message) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setId(message.getId());
        dto.setRole(message.getRole());
        dto.setContent(message.getContent());
        dto.setCreatedAt(message.getCreatedAt());
        if (message.getSources() != null) {
            try {
                dto.setSources(objectMapper.readValue(message.getSources(), List.class));
            } catch (Exception e) {
                // ignore
            }
        }
        return dto;
    }
    
    @PutMapping("/{conversationId}/public")
    public ResponseEntity<Void> toggleConversationPublicStatus(
            @PathVariable Long conversationId,
            @RequestBody java.util.Map<String, Object> body,
            @CurrentUser Long userId) {
        Boolean isPublic = (Boolean) body.get("isPublic");
        String allowedUsers = (String) body.get("allowedUsers");
        
        if (isPublic == null) {
            return ResponseEntity.badRequest().build();
        }
        conversationService.toggleConversationPublicStatus(conversationId, isPublic, allowedUsers, userId);
        return ResponseEntity.ok().build();
    }
}