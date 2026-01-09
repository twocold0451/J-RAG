package com.twocold.jrag.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twocold.jrag.api.dto.*;
import com.twocold.jrag.config.CurrentUser;
import com.twocold.jrag.domain.ChatMessage;
import com.twocold.jrag.domain.Conversation;
import com.twocold.jrag.domain.Document;
import com.twocold.jrag.repository.UserRepository;
import com.twocold.jrag.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 对话管理控制器
 * 负责对话的创建、删除、列表查询及元数据管理。
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "对话管理", description = "负责对话的创建、删除、列表查询及元数据管理。")
public class ConversationController {

    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Operation(summary = "创建新对话")
    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @Parameter(hidden = true) @CurrentUser Long userId,
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

    @Operation(summary = "获取我的对话列表")
    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getConversations(@Parameter(hidden = true) @CurrentUser Long userId) {
        List<Conversation> conversations = conversationService.getConversationsForUser(userId);
        return ResponseEntity.ok(conversations.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "获取公开对话列表(团队)")
    @GetMapping("/public")
    public ResponseEntity<List<ConversationResponse>> getPublicConversations() {
        List<Conversation> conversations = conversationService.getPublicConversations();
        return ResponseEntity.ok(conversations.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "获取公开对话的消息")
    @GetMapping("/{conversationId}/public/messages")
    public ResponseEntity<List<ChatMessageDto>> getPublicChatMessages(
            @Parameter(description = "对话 ID") @PathVariable Long conversationId) {
        List<ChatMessage> messages = conversationService.getPublicChatMessages(conversationId);
        return ResponseEntity.ok(messages.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "删除对话")
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @Parameter(description = "对话 ID") @PathVariable Long conversationId,
            @Parameter(hidden = true) @CurrentUser Long userId) {
        conversationService.deleteConversation(conversationId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "清空对话历史消息")
    @DeleteMapping("/{conversationId}/messages")
    public ResponseEntity<Void> clearConversationMessages(
            @Parameter(description = "对话 ID") @PathVariable Long conversationId,
            @Parameter(hidden = true) @CurrentUser Long userId) {
        conversationService.clearConversationMessages(conversationId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "删除单条消息")
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @Parameter(description = "消息 ID") @PathVariable Long messageId,
            @Parameter(hidden = true) @CurrentUser Long userId) {
        conversationService.deleteMessage(messageId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "获取对话的消息列表")
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getChatMessages(
            @Parameter(description = "对话 ID") @PathVariable Long conversationId,
            @Parameter(hidden = true) @CurrentUser Long userId) {
        List<ChatMessage> messages = conversationService.getChatMessagesForConversation(conversationId, userId);
        return ResponseEntity.ok(messages.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "向对话中添加文档")
    @PostMapping("/{conversationId}/documents")
    public ResponseEntity<Void> addDocuments(
            @Parameter(description = "对话 ID") @PathVariable Long conversationId,
            @Parameter(hidden = true) @CurrentUser Long userId,
            @RequestBody AddDocumentsRequest request) {
        conversationService.addDocumentsToConversation(conversationId, userId, request.documentIds());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "从对话中移除文档")
    @DeleteMapping("/{conversationId}/documents/{documentId}")
    public ResponseEntity<Void> removeDocument(
            @Parameter(description = "对话 ID") @PathVariable Long conversationId,
            @Parameter(description = "文档 ID") @PathVariable UUID documentId,
            @Parameter(hidden = true) @CurrentUser Long userId) {
        conversationService.removeDocumentFromConversation(conversationId, userId, documentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "获取对话关联的文档列表")
    @GetMapping("/{conversationId}/documents")
    public ResponseEntity<List<DocumentDto>> getConversationDocuments(
            @Parameter(description = "对话 ID") @PathVariable Long conversationId,
            @Parameter(hidden = true) @CurrentUser Long userId) {
        List<Document> documents = conversationService.getDocumentsForConversation(conversationId, userId);
        return ResponseEntity.ok(documents.stream()
                .map(DocumentDto::from)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "切换对话公开状态")
    @PutMapping("/{conversationId}/public")
    public ResponseEntity<Void> toggleConversationPublicStatus(
            @Parameter(description = "对话 ID") @PathVariable Long conversationId,
            @RequestBody java.util.Map<String, Object> body,
            @Parameter(hidden = true) @CurrentUser Long userId) {
        Boolean isPublic = (Boolean) body.get("isPublic");
        String allowedUsers = (String) body.get("allowedUsers");
        
        if (isPublic == null) {
            return ResponseEntity.badRequest().build();
        }
        conversationService.toggleConversationPublicStatus(conversationId, isPublic, allowedUsers, userId);
        return ResponseEntity.ok().build();
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
}
