package com.twocold.jrag.service;

import com.twocold.jrag.api.dto.TemplateCreateRequest;
import com.twocold.jrag.api.dto.TemplateDto;
import com.twocold.jrag.domain.Template;
import com.twocold.jrag.domain.TemplateDocument;
import com.twocold.jrag.repository.TemplateDocumentRepository;
import com.twocold.jrag.repository.TemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateDocumentRepository templateDocumentRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TemplateDto> listVisibleTemplates(Long userId, boolean isAdmin) {
        Iterable<Template> templates;
        if (isAdmin) {
             templates = templateRepository.findAll();
        } else {
             // Logic: public + visible to user's groups
             // Since we have a complex query in repository, we use it
             templates = templateRepository.findVisibleTemplatesForUser(userId);
             // Also include templates created by the user themselves if not already covered?
             // The requirement says "Visible templates". Usually includes own templates.
             // We can merge results or assume the query covers it if we add "OR owner = user" logic there.
             // For now, let's stick to the Repo query.
        }
        
        return StreamSupport.stream(templates.spliterator(), false)
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public TemplateDto getTemplate(Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        return mapToDto(template);
    }

    @Transactional
    public TemplateDto createTemplate(TemplateCreateRequest request, Long userId) {
        Template template = new Template();
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setIcon(request.getIcon());
        template.setUserId(userId);
        template.setPublic(request.isPublic());
        template.setCreatedAt(OffsetDateTime.now());
        template.setUpdatedAt(OffsetDateTime.now());
        
        if (request.getVisibleGroupIds() != null) {
            try {
                template.setVisibleGroups(objectMapper.writeValueAsString(request.getVisibleGroupIds()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing visible groups", e);
            }
        }
        
        Template saved = templateRepository.save(template);
        
        if (request.getDocumentIds() != null) {
            for (UUID docId : request.getDocumentIds()) {
                TemplateDocument td = new TemplateDocument();
                td.setTemplateId(saved.getId());
                td.setDocumentId(docId);
                td.setCreatedAt(OffsetDateTime.now());
                templateDocumentRepository.save(td);
            }
        }
        
        return mapToDto(saved);
    }

    @Transactional
    public TemplateDto updateTemplate(Long templateId, TemplateCreateRequest request) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setIcon(request.getIcon());
        template.setPublic(request.isPublic());
        template.setUpdatedAt(OffsetDateTime.now());
        
        if (request.getVisibleGroupIds() != null) {
             try {
                template.setVisibleGroups(objectMapper.writeValueAsString(request.getVisibleGroupIds()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing visible groups", e);
            }
        } else {
            template.setVisibleGroups(null);
        }
        
        Template saved = templateRepository.save(template);
        
        // Update documents
        templateDocumentRepository.deleteByTemplateId(templateId);
        if (request.getDocumentIds() != null) {
            for (UUID docId : request.getDocumentIds()) {
                TemplateDocument td = new TemplateDocument();
                td.setTemplateId(saved.getId());
                td.setDocumentId(docId);
                td.setCreatedAt(OffsetDateTime.now());
                templateDocumentRepository.save(td);
            }
        }
        
        return mapToDto(saved);
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        templateDocumentRepository.deleteByTemplateId(templateId);
        templateRepository.deleteById(templateId);
    }

    private TemplateDto mapToDto(Template template) {
        TemplateDto dto = new TemplateDto();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setIcon(template.getIcon());
        dto.setPublic(template.isPublic());
        dto.setCreatedAt(template.getCreatedAt());
        
        if (template.getVisibleGroups() != null) {
            try {
                dto.setVisibleGroups(objectMapper.readValue(template.getVisibleGroups(), new TypeReference<List<Long>>(){}));
            } catch (JsonProcessingException e) {
                dto.setVisibleGroups(Collections.emptyList());
            }
        }
        
        List<TemplateDocument> docs = templateDocumentRepository.findByTemplateId(template.getId());
        dto.setDocumentCount(docs.size());
        dto.setDocumentIds(docs.stream().map(TemplateDocument::getDocumentId).collect(Collectors.toList()));
        
        return dto;
    }
}
