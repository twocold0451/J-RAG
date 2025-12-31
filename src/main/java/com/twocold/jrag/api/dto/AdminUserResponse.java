package com.twocold.jrag.api.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminUserResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    private List<Long> groupIds;
    private int conversationCount;
    private LocalDateTime lastLoginAt;
    private String initialPassword; // Only for creation response
}
