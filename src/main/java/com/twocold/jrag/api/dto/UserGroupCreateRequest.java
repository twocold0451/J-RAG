package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "创建用户组请求")
public record UserGroupCreateRequest(
    @Schema(description = "用户组名称", example = "研发部")
    String name,
    
    @Schema(description = "用户组描述")
    String description,
    
    @Schema(description = "包含的用户 ID 列表")
    List<Long> userIds
) {}
