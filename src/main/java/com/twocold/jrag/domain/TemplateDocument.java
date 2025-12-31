package com.twocold.jrag.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Table("template_documents")
public class TemplateDocument {
    @Id
    private Long id;
    private Long templateId;
    private UUID documentId;
    private OffsetDateTime createdAt;
}
