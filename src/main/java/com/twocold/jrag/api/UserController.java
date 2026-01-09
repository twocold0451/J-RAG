package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.LoginRequest;
import com.twocold.jrag.api.dto.RegisterRequest;
import com.twocold.jrag.api.dto.UserResponse;
import com.twocold.jrag.config.CurrentUser;
import com.twocold.jrag.config.JwtUtil;
import com.twocold.jrag.domain.User;
import com.twocold.jrag.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证控制器
 * 负责用户注册、登录、获取个人信息及修改密码。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "用户认证", description = "用户注册、登录及密码管理接口。")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        User user = userService.register(request.username(), request.password(), request.email());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return ResponseEntity.ok(UserResponse.from(user, token));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest request) {
        User user = userService.login(request.username(), request.password());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return ResponseEntity.ok(UserResponse.from(user, token));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@Parameter(hidden = true) @CurrentUser Long userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.findById(userId);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Parameter(hidden = true) @CurrentUser Long userId,
            @RequestBody java.util.Map<String, String> request) {
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");
        userService.changePassword(userId, currentPassword, newPassword);
        return ResponseEntity.ok().build();
    }
}