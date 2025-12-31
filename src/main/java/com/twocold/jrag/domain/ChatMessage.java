package com.twocold.jrag.domain;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Setter
@Getter
@Data
@Table("chat_messages")
public class ChatMessage {
    @Id
    private Long id;
    private Long conversationId;
    private String role; // USER, ASSISTANT, SYSTEM
    private String content;
    private LocalDateTime createdAt;

}