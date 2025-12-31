package com.twocold.jrag.api.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class TemplateCreateRequest {
    private String name;
    private String description;
    private String icon;
    private List<UUID> documentIds;
    private List<Long> visibleGroupIds;
    private boolean isPublic;
}
