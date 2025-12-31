package com.twocold.jrag.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageDto {
    private Long id;
    private String role; // USER, ASSISTANT, SYSTEM
    private String content;
    private LocalDateTime createdAt;
}
