package com.example.qarag.api.dto;

import jakarta.validation.constraints.NotEmpty;

public record QaRequest(@NotEmpty String question) {
}
