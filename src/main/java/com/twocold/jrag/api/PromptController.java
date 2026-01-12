package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.PromptUpdateRequest;
import com.twocold.jrag.service.PromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/prompts")
@RequiredArgsConstructor
@Tag(name = "Prompt 管理", description = "管理系统提示词，支持热更新")
public class PromptController {

    private final PromptService promptService;

    @Operation(summary = "获取所有 Prompts")
    @GetMapping
    public ResponseEntity<Map<String, String>> getAllPrompts() {
        return ResponseEntity.ok(promptService.getAllPrompts());
    }

    @Operation(summary = "更新指定 Prompt")
    @PutMapping("/{key}")
    public ResponseEntity<Void> updatePrompt(
            @PathVariable String key,
            @RequestBody PromptUpdateRequest request) {
        promptService.updatePrompt(key, request.getContent(), request.getDescription());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "刷新 Prompt 缓存 (从数据库)")
    @PostMapping("/refresh")
    public ResponseEntity<Void> refreshPrompts() {
        promptService.refreshCache();
        return ResponseEntity.ok().build();
    }
}
