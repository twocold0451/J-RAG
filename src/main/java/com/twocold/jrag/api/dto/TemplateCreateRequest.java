package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "创建模板请求")
public class TemplateCreateRequest {
    @Schema(description = "模板名称", example = "新员工入职助手")
    private String name;
    
    @Schema(description = "模板描述")
    private String description;
    
    @Schema(description = "图标标识")
    private String icon;
    
    @Schema(description = "预置的文档 ID 列表")
    private List<UUID> documentIds;
    
    @Schema(description = "可见用户组 ID 列表")
    private List<Long> visibleGroupIds;
    
    @Schema(description = "是否设为公共模板")
    private boolean isPublic;
}