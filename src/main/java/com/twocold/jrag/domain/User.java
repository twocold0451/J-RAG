package com.twocold.jrag.domain;

import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("users")
public class User {
    @Getter
    @Id
    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private String salt;
    private String role; // New field
    private LocalDateTime createdAt;
}