package com.twocold.jrag.api.dto;

import com.twocold.jrag.domain.DocumentStatus;

import java.util.UUID;

public record DocumentUpdateMessage(
        UUID documentId,
        DocumentStatus status,
        Integer progress,
        String errorMessage
) {
}