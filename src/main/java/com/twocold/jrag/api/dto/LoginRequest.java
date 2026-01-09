package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户登录请求")
public record LoginRequest(
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin")
    String username,
    
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    String password
) {}