package com.example.qarag.api;

import com.example.qarag.api.dto.LoginRequest;
import com.example.qarag.api.dto.RegisterRequest;
import com.example.qarag.api.dto.UserResponse;
import com.example.qarag.config.CurrentUser;
import com.example.qarag.domain.User;
import com.example.qarag.service.UserService;
import com.example.qarag.config.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        User user = userService.register(request.getUsername(), request.getPassword(), request.getEmail());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return ResponseEntity.ok(UserResponse.from(user, token));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest request) {
        User user = userService.login(request.getUsername(), request.getPassword());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return ResponseEntity.ok(UserResponse.from(user, token));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@CurrentUser Long userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.findById(userId);
        return ResponseEntity.ok(UserResponse.from(user));
    }
}
