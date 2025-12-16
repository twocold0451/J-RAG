package com.example.qarag.api.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class AddDocumentsRequest {
    private List<UUID> documentIds;

    public List<UUID> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<UUID> documentIds) {
        this.documentIds = documentIds;
    }
}