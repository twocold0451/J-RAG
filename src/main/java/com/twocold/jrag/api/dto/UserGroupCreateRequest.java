package com.twocold.jrag.api.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserGroupCreateRequest {
    private String name;
    private String description;
    private List<Long> userIds;
}
