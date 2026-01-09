package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.AdminUserCreateRequest;
import com.twocold.jrag.api.dto.AdminUserResponse;
import com.twocold.jrag.domain.User;
import com.twocold.jrag.domain.UserGroupMember;
import com.twocold.jrag.repository.UserGroupMemberRepository;
import com.twocold.jrag.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 管理员控制器
 * 提供用户管理、密码重置等管理员权限操作。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "管理员", description = "提供用户管理、密码重置等管理员权限操作。")
public class AdminController {

    private final UserService userService;
    private final UserGroupMemberRepository userGroupMemberRepository;

    @Operation(summary = "创建新用户")
    @PostMapping("/users")
    @Transactional
    public ResponseEntity<AdminUserResponse> createUser(@RequestBody AdminUserCreateRequest request) {
        String initialPassword = UUID.randomUUID().toString().substring(0, 8); // Simple random password
        User user = userService.createUser(request.getUsername(), request.getEmail(), request.getRole(), initialPassword);
        
        if (request.getGroupIds() != null) {
            for (Long groupId : request.getGroupIds()) {
                UserGroupMember member = new UserGroupMember();
                member.setGroupId(groupId);
                member.setUserId(user.getId());
                member.setCreatedAt(OffsetDateTime.now());
                userGroupMemberRepository.save(member);
            }
        }
        
        AdminUserResponse response = mapToAdminResponse(user);
        response.setInitialPassword(initialPassword);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "列出所有用户")
    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> listUsers() {
        List<AdminUserResponse> responses = StreamSupport.stream(userService.findAll().spliterator(), false)
                .map(this::mapToAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
    
    @Operation(summary = "更新用户信息")
    @PutMapping("/users/{userId}")
    @Transactional
    public ResponseEntity<AdminUserResponse> updateUser(
            @Parameter(description = "用户 ID") @PathVariable Long userId,
            @RequestBody AdminUserCreateRequest request) {
        User user = userService.updateUser(userId, request.getUsername(), request.getRole(), request.getEmail());
        
        if (request.getGroupIds() != null) {
            List<UserGroupMember> existing = userGroupMemberRepository.findByUserId(userId);
             userGroupMemberRepository.deleteAll(existing);
             
             for (Long groupId : request.getGroupIds()) {
                UserGroupMember member = new UserGroupMember();
                member.setGroupId(groupId);
                member.setUserId(user.getId());
                member.setCreatedAt(OffsetDateTime.now());
                userGroupMemberRepository.save(member);
            }
        }
        
        return ResponseEntity.ok(mapToAdminResponse(user));
    }

    @Operation(summary = "重置用户密码")
    @PostMapping("/users/{userId}/reset-password")
    @Transactional
    public ResponseEntity<Map<String, String>> resetPassword(@Parameter(description = "用户 ID") @PathVariable Long userId) {
        String newPassword = userService.resetPasswordWithRandom(userId);
        return ResponseEntity.ok(Map.of("newPassword", newPassword));
    }

    private AdminUserResponse mapToAdminResponse(User user) {
        AdminUserResponse response = new AdminUserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        
        List<UserGroupMember> members = userGroupMemberRepository.findByUserId(user.getId());
        response.setGroupIds(members.stream().map(UserGroupMember::getGroupId).collect(Collectors.toList()));
        
        return response;
    }
}