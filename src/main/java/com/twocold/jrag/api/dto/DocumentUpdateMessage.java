package com.twocold.jrag.api.dto;

import com.twocold.jrag.domain.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "文档处理状态更新消息 (常用于 WebSocket 通知)")
public record DocumentUpdateMessage(
        @Schema(description = "文档唯一 ID")
        UUID documentId,
        
        @Schema(description = "当前处理状态")
        DocumentStatus status,
        
        @Schema(description = "处理进度百分比 (0-100)")
        Integer progress,
        
        @Schema(description = "错误消息 (如果状态为 FAILED)")
        String errorMessage
) {
}
