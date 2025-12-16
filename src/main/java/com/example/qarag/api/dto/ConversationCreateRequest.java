package com.example.qarag.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ConversationCreateRequest {
    private String title;
    private List<UUID> documentIds;
    @JsonProperty("isPublic")
    private boolean isPublic;
    private Long parentId;
    private String allowedUsers; // New field

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<UUID> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<UUID> documentIds) {
        this.documentIds = documentIds;
    }
}