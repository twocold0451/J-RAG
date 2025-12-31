package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.TemplateCreateRequest;
import com.twocold.jrag.api.dto.TemplateDto;
import com.twocold.jrag.config.CurrentUser;
import com.twocold.jrag.service.TemplateService;
import com.twocold.jrag.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<TemplateDto>> listTemplates(@CurrentUser Long userId) {
        boolean isAdmin = userService.isAdmin(userId);
        return ResponseEntity.ok(templateService.listVisibleTemplates(userId, isAdmin));
    }

    @PostMapping
    public ResponseEntity<TemplateDto> createTemplate(@CurrentUser Long userId, @RequestBody TemplateCreateRequest request) {
        // Optional: Check if user has permission to create templates
        return ResponseEntity.ok(templateService.createTemplate(request, userId));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<TemplateDto> getTemplate(@PathVariable Long templateId) {
        return ResponseEntity.ok(templateService.getTemplate(templateId));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<TemplateDto> updateTemplate(@PathVariable Long templateId, @RequestBody TemplateCreateRequest request) {
        return ResponseEntity.ok(templateService.updateTemplate(templateId, request));
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long templateId) {
        templateService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }
}
