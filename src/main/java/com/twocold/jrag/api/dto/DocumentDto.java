package com.twocold.jrag.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.twocold.jrag.domain.Document;
import com.twocold.jrag.domain.DocumentStatus; // Import DocumentStatus
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class DocumentDto {
    private UUID id;
    private String name;
    private OffsetDateTime uploadedAt;
    private DocumentStatus status; // Add status field
    private Integer progress;     // Add progress field
    private String errorMessage;  // Add errorMessage field
    private Long userId;          // Add userId field
    @JsonProperty("isPublic")
    private boolean isPublic;     // Add isPublic field
    private String category;      // Add category field

    public static DocumentDto from(Document document) {
        DocumentDto dto = new DocumentDto();
        dto.setId(document.getId());
        dto.setName(document.getName());
        dto.setUploadedAt(document.getUploadedAt());
        dto.setStatus(document.getStatus());         // Set status
        dto.setProgress(document.getProgress());       // Set progress
        dto.setErrorMessage(document.getErrorMessage()); // Set errorMessage
        dto.setUserId(document.getUserId());           // Set userId
        dto.setPublic(document.isPublic());            // Set isPublic
        dto.setCategory(document.getCategory());       // Set category
        return dto;
    }
}
