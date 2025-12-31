package com.twocold.jrag.api.dto;

import jakarta.validation.constraints.NotEmpty;

public record QaRequest(@NotEmpty String question) {
}
