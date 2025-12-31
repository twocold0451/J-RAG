package com.twocold.jrag.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("user_group_members")
public class UserGroupMember {
    @Id
    private Long id;
    private Long groupId;
    private Long userId;
    private OffsetDateTime createdAt;
}
