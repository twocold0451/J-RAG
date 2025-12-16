package com.example.qarag.api.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}