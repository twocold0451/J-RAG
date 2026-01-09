package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.UserGroupCreateRequest;
import com.twocold.jrag.api.dto.UserGroupDto;
import com.twocold.jrag.service.UserGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户组控制器
 * 负责用户组的 CRUD 管理。
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "用户组管理", description = "负责用户组的 CRUD 管理。")
public class UserGroupController {

    private final UserGroupService userGroupService;

    @Operation(summary = "列出所有用户组")
    @GetMapping
    public ResponseEntity<List<UserGroupDto>> getAllGroups() {
        return ResponseEntity.ok(userGroupService.getAllGroups());
    }

    @Operation(summary = "创建用户组")
    @PostMapping
    public ResponseEntity<UserGroupDto> createGroup(@RequestBody UserGroupCreateRequest request) {
        return ResponseEntity.ok(userGroupService.createGroup(request));
    }

    @Operation(summary = "更新用户组")
    @PutMapping("/{groupId}")
    public ResponseEntity<UserGroupDto> updateGroup(
            @Parameter(description = "用户组 ID") @PathVariable Long groupId,
            @RequestBody UserGroupCreateRequest request) {
        return ResponseEntity.ok(userGroupService.updateGroup(groupId, request));
    }

    @Operation(summary = "删除用户组")
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@Parameter(description = "用户组 ID") @PathVariable Long groupId) {
        userGroupService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }
    
    @Operation(summary = "获取用户组成员")
    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<Long>> getGroupMembers(@Parameter(description = "用户组 ID") @PathVariable Long groupId) {
        return ResponseEntity.ok(userGroupService.getGroupMemberIds(groupId));
    }
}