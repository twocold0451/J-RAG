package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.TemplateCreateRequest;
import com.twocold.jrag.api.dto.TemplateDto;
import com.twocold.jrag.config.CurrentUser;
import com.twocold.jrag.service.TemplateService;
import com.twocold.jrag.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模板控制器
 * 负责对话模板的 CRUD 管理。
 */
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Tag(name = "对话模板", description = "负责对话模板的 CRUD 管理。")
public class TemplateController {

    private final TemplateService templateService;
    private final UserService userService;

    @Operation(summary = "列出可用模板")
    @GetMapping
    public ResponseEntity<List<TemplateDto>> listTemplates(@Parameter(hidden = true) @CurrentUser Long userId) {
        boolean isAdmin = userService.isAdmin(userId);
        return ResponseEntity.ok(templateService.listVisibleTemplates(userId, isAdmin));
    }

    @Operation(summary = "创建新模板")
    @PostMapping
    public ResponseEntity<TemplateDto> createTemplate(
            @Parameter(hidden = true) @CurrentUser Long userId,
            @RequestBody TemplateCreateRequest request) {
        // TODO: 权限检查
        return ResponseEntity.ok(templateService.createTemplate(request, userId));
    }

    @Operation(summary = "获取模板详情")
    @GetMapping("/{templateId}")
    public ResponseEntity<TemplateDto> getTemplate(@Parameter(description = "模板 ID") @PathVariable Long templateId) {
        return ResponseEntity.ok(templateService.getTemplate(templateId));
    }

    @Operation(summary = "更新模板")
    @PutMapping("/{templateId}")
    public ResponseEntity<TemplateDto> updateTemplate(
            @Parameter(description = "模板 ID") @PathVariable Long templateId,
            @RequestBody TemplateCreateRequest request) {
        // TODO: 权限检查
        return ResponseEntity.ok(templateService.updateTemplate(templateId, request));
    }

    @Operation(summary = "删除模板")
    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@Parameter(description = "模板 ID") @PathVariable Long templateId) {
        // TODO: 权限检查
        templateService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }
}