package com.example.qarag.api.dto;

import java.util.UUID;

public record UploadResponse(UUID documentId, String message, boolean isPublic) {
}
