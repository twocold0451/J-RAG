package com.twocold.jrag.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.twocold.jrag.domain.Document;
import com.twocold.jrag.domain.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Schema(description = "文档信息对象")
public class DocumentDto {
    
    @Schema(description = "文档唯一 ID")
    private UUID id;
    
    @Schema(description = "文档名称")
    private String name;
    
    @Schema(description = "上传时间")
    private OffsetDateTime uploadedAt;
    
    @Schema(description = "文档处理状态 (PENDING, PROCESSING, COMPLETED, FAILED)")
    private DocumentStatus status;
    
    @Schema(description = "处理进度 (0-100)")
    private Integer progress;
    
    @Schema(description = "错误信息（如果处理失败）")
    private String errorMessage;
    
    @Schema(description = "上传者用户 ID")
    private Long userId;
    
    @Schema(description = "是否公开")
    @JsonProperty("isPublic")
    private boolean isPublic;
    
    @Schema(description = "分类标签")
    private String category;

    public static DocumentDto from(Document document) {
        DocumentDto dto = new DocumentDto();
        dto.setId(document.getId());
        dto.setName(document.getName());
        dto.setUploadedAt(document.getUploadedAt());
        dto.setStatus(document.getStatus());
        dto.setProgress(document.getProgress());
        dto.setErrorMessage(document.getErrorMessage());
        dto.setUserId(document.getUserId());
        dto.setPublic(document.isPublic());
        dto.setCategory(document.getCategory());
        return dto;
    }
}