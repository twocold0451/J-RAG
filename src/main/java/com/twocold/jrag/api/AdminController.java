package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.AdminUserCreateRequest;
import com.twocold.jrag.api.dto.AdminUserResponse;
import com.twocold.jrag.domain.User;
import com.twocold.jrag.domain.UserGroupMember;
import com.twocold.jrag.repository.UserGroupMemberRepository;
import com.twocold.jrag.service.UserGroupService;
import com.twocold.jrag.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final UserGroupService userGroupService;
    private final UserGroupMemberRepository userGroupMemberRepository;

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

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> listUsers() {
        List<AdminUserResponse> responses = StreamSupport.stream(userService.findAll().spliterator(), false)
                .map(this::mapToAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
    
    @PutMapping("/users/{userId}")
    @Transactional
    public ResponseEntity<AdminUserResponse> updateUser(@PathVariable Long userId, @RequestBody AdminUserCreateRequest request) {
        User user = userService.updateUser(userId, request.getUsername(), request.getRole(), request.getEmail());
        
        // Update groups
        if (request.getGroupIds() != null) {
            // Remove all existing groups for this user (Wait, UserGroupService updates by group ID, here we update by User ID)
            // I need a method to delete by User ID in Repo.
            // Or fetch existing and diff.
            // Let's assume we replace all.
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

    private AdminUserResponse mapToAdminResponse(User user) {
        AdminUserResponse response = new AdminUserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        // response.setLastLoginAt(...) // Need to track login time in User entity if needed
        
        List<UserGroupMember> members = userGroupMemberRepository.findByUserId(user.getId());
        response.setGroupIds(members.stream().map(UserGroupMember::getGroupId).collect(Collectors.toList()));
        
        return response;
    }
}
