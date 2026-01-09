package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "上传响应")
public record UploadResponse(
        @Schema(description = "生成的文档 ID") UUID documentId,
        @Schema(description = "操作结果消息") String message,
        @Schema(description = "文档公开状态") boolean isPublic
) {
}