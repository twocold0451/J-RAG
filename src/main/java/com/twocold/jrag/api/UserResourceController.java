package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.UserGroupDto;
import com.twocold.jrag.config.CurrentUser;
import com.twocold.jrag.service.UserGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserResourceController {

    private final UserGroupService userGroupService;

    @GetMapping("/groups")
    public ResponseEntity<List<UserGroupDto>> getMyGroups(@CurrentUser Long userId) {
        return ResponseEntity.ok(userGroupService.getGroupsForUser(userId));
    }
}
