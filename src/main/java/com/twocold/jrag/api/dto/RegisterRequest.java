package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户注册请求")
public record RegisterRequest(
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    String username,
    
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
    String password,
    
    @Schema(description = "电子邮箱")
    String email
) {}