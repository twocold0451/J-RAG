package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "对话模板信息")
public class TemplateDto {
    @Schema(description = "模板 ID")
    private Long id;
    
    @Schema(description = "模板名称")
    private String name;
    
    @Schema(description = "模板描述")
    private String description;
    
    @Schema(description = "图标标识")
    private String icon;
    
    @Schema(description = "关联文档数量")
    private int documentCount;
    
    @Schema(description = "是否公开")
    private boolean isPublic;
    
    @Schema(description = "可见用户组 ID 列表")
    private List<Long> visibleGroups;
    
    @Schema(description = "关联文档 ID 列表（详情模式下返回）")
    private List<UUID> documentIds; 
    
    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;
}