package com.twocold.jrag.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationResponse {
    private Long id;
    private Long userId; // Add userId field
    private String username;
    private Long templateId;
    private String title;
    @JsonProperty("isPublic")
    private boolean isPublic;
    private Long parentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
