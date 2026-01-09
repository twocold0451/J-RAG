package com.twocold.jrag.api.dto;

import com.twocold.jrag.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Schema(description = "用户信息响应")
public class UserResponse {
    @Setter
    @Getter
    @Schema(description = "用户 ID")
    private Long id;
    
    @Setter
    @Getter
    @Schema(description = "用户名")
    private String username;
    
    @Setter
    @Getter
    @Schema(description = "电子邮箱")
    private String email;
    
    @Schema(description = "角色权限")
    private String role; 
    
    @Setter
    @Getter
    @Schema(description = "JWT 认证令牌（登录/注册成功时返回）")
    private String token;

    public static UserResponse from(User user) {
        return from(user, null);
    }

    public static UserResponse from(User user, String token) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setToken(token);
        return response;
    }

}
