package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.UserGroupCreateRequest;
import com.twocold.jrag.api.dto.UserGroupDto;
import com.twocold.jrag.service.UserGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class UserGroupController {

    private final UserGroupService userGroupService;

    @GetMapping
    public ResponseEntity<List<UserGroupDto>> getAllGroups() {
        return ResponseEntity.ok(userGroupService.getAllGroups());
    }

    @PostMapping
    public ResponseEntity<UserGroupDto> createGroup(@RequestBody UserGroupCreateRequest request) {
        return ResponseEntity.ok(userGroupService.createGroup(request));
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<UserGroupDto> updateGroup(@PathVariable Long groupId, @RequestBody UserGroupCreateRequest request) {
        return ResponseEntity.ok(userGroupService.updateGroup(groupId, request));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long groupId) {
        userGroupService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<Long>> getGroupMembers(@PathVariable Long groupId) {
        return ResponseEntity.ok(userGroupService.getGroupMemberIds(groupId));
    }
}
