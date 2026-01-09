package com.twocold.jrag.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "创建对话请求")
public class ConversationCreateRequest {
    @Setter
    @Getter
    @Schema(description = "对话标题")
    private String title;
    
    @Schema(description = "基于的模板 ID")
    private Long templateId;
    
    @Setter
    @Getter
    @Schema(description = "初始关联的文档 ID 列表")
    private List<UUID> documentIds;
    
    @JsonProperty("isPublic")
    @Schema(description = "是否设为公开对话")
    private boolean isPublic;
    
    @Schema(description = "父级对话 ID")
    private Long parentId;
    
    @Schema(description = "允许访问的用户列表")
    private String allowedUsers;

}
