package com.example.qarag.api.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Data
public class ChatRequest {
    private String message;
    private boolean useDeepThinking;

}