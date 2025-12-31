package com.twocold.jrag.api.dto;

import lombok.Data;
import java.util.List;

@Data
public class AdminUserCreateRequest {
    private String username;
    private String email;
    private String role;
    private List<Long> groupIds;
}
