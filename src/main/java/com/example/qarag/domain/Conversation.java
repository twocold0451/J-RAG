package com.example.qarag.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("conversations")
public class Conversation {
    @Id
    private Long id;
    private Long userId;
    private String title;
    private boolean isPublic; // 新字段
    private Long parentId;    // 新字段
    private String allowedUsers; // 用于白名单的新字段
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}