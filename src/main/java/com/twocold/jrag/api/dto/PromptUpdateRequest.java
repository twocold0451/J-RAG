package com.twocold.jrag.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PromptUpdateRequest {
    @NotBlank
    private String content;
    
    private String description;
}
