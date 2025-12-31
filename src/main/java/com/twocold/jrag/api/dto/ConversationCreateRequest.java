package com.twocold.jrag.api.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Data
public class ConversationCreateRequest {
    @Setter
    @Getter
    private String title;
    private Long templateId;
    @Setter
    @Getter
    private List<UUID> documentIds;
    private boolean isPublic;
    private Long parentId;
    private String allowedUsers; // New field

}