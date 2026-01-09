package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.ChatRequest;
import com.twocold.jrag.config.CurrentUser;
import com.twocold.jrag.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 聊天交互控制器
 * 负责处理实时对话、流式响应（SSE）以及深度思考模式。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "聊天交互", description = "处理 RAG 对话、Agent 深度思考等实时交互接口。")
public class ChatController {

    private final ChatService chatService;

    /**
     * 流式对话接口 (SSE)
     * 支持标准 RAG 检索对话和基于 ReAct 范式的深度思考模式。
     */
    @Operation(summary = "流式对话 (SSE)", description = "通过 Server-Sent Events 发送 AI 回复。支持标准检索增强生成和深度思考模式。")
    @PostMapping("/chat/{conversationId}/stream")
    public SseEmitter streamChat(
            @Parameter(description = "目标对话的 ID") @PathVariable Long conversationId,
            @Parameter(hidden = true) @CurrentUser Long userId,
            @Parameter(description = "聊天请求，包含用户消息和配置") @RequestBody ChatRequest request) {
        // 超时时间设置为 3 分钟 (180000 毫秒)，以适应较长的思考过程
        SseEmitter emitter = new SseEmitter(180000L);
        chatService.streamChat(conversationId, userId, request.message(), request.useDeepThinking(), emitter);
        return emitter;
    }
}