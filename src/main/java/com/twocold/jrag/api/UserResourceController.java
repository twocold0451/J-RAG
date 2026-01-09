package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.UserGroupDto;
import com.twocold.jrag.config.CurrentUser;
import com.twocold.jrag.service.UserGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 普通用户资源控制器
 * 提供普通用户获取自身相关资源的接口。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户资源", description = "提供普通用户获取自身相关资源的接口。")
public class UserResourceController {

    private final UserGroupService userGroupService;

    @Operation(summary = "获取我所在的用户组")
    @GetMapping("/groups")
    public ResponseEntity<List<UserGroupDto>> getMyGroups(@Parameter(hidden = true) @CurrentUser Long userId) {
        return ResponseEntity.ok(userGroupService.getGroupsForUser(userId));
    }
}