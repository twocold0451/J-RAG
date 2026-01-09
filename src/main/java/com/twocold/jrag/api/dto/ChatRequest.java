package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "聊天请求参数")
public record ChatRequest(
    @Schema(description = "用户发送的消息内容", example = "介绍一下 Docker 的原理")
    String message,
    
    @Schema(description = "是否启用深度思考模式 (Agentic RAG)，启用后会进行查询分解和多步推理", defaultValue = "false")
    boolean useDeepThinking
) {}