package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "管理员创建用户请求")
public class AdminUserCreateRequest {
    @Schema(description = "用户名", example = "john_doe")
    private String username;
    
    @Schema(description = "电子邮箱", example = "john@example.com")
    private String email;
    
    @Schema(description = "用户角色 (USER, ADMIN)", example = "USER")
    private String role;
    
    @Schema(description = "关联的用户组 ID 列表")
    private List<Long> groupIds;
}