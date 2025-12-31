package com.twocold.jrag.api.dto;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class UserGroupDto {
    private Long id;
    private String name;
    private String description;
    private int memberCount;
    private int templateCount;
    private OffsetDateTime createdAt;
}
