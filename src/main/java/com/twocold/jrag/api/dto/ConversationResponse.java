package com.twocold.jrag.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "对话信息响应")
public class ConversationResponse {
    @Schema(description = "对话 ID")
    private Long id;
    
    @Schema(description = "创建者用户 ID")
    private Long userId;
    
    @Schema(description = "创建者用户名")
    private String username;
    
    @Schema(description = "关联模板 ID")
    private Long templateId;
    
    @Schema(description = "对话标题")
    private String title;
    
    @JsonProperty("isPublic")
    @Schema(description = "是否公开")
    private boolean isPublic;
    
    @Schema(description = "父级对话 ID")
    private Long parentId;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}