package com.example.qarag.api.dto;

import com.example.qarag.domain.DocumentStatus;

import java.util.UUID;

public record DocumentUpdateMessage(
        UUID documentId,
        DocumentStatus status,
        Integer progress,
        String errorMessage
) {
}