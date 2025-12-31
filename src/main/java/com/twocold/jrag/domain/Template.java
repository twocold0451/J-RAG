package com.twocold.jrag.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("templates")
public class Template {
    @Id
    private Long id;
    private String name;
    private String description;
    private String icon;
    private Long userId;
    private boolean isPublic;
    private String visibleGroups; // JSON string of group IDs
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
