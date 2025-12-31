package com.twocold.jrag.api.dto;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TemplateDto {
    private Long id;
    private String name;
    private String description;
    private String icon;
    private int documentCount;
    private boolean isPublic;
    private List<Long> visibleGroups;
    private List<UUID> documentIds; // Optional: detailed view
    private OffsetDateTime createdAt;
}
