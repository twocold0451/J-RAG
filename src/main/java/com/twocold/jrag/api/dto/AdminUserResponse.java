package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "管理员视图下的用户信息")
public class AdminUserResponse {
    @Schema(description = "用户 ID")
    private Long id;
    
    @Schema(description = "用户名")
    private String username;
    
    @Schema(description = "电子邮箱")
    private String email;
    
    @Schema(description = "用户角色")
    private String role;
    
    @Schema(description = "所属用户组 ID 列表")
    private List<Long> groupIds;
    
    @Schema(description = "关联对话数量")
    private int conversationCount;
    
    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginAt;
    
    @Schema(description = "初始密码（仅在创建成功时返回）")
    private String initialPassword; 
}