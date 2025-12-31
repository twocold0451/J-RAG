package com.twocold.jrag.api.dto;

import com.twocold.jrag.domain.User;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class UserResponse {
    @Setter
    @Getter
    private Long id;
    @Setter
    @Getter
    private String username;
    @Setter
    @Getter
    private String email;
    private String role; // Add role
    @Setter
    @Getter
    private String token;

    public static UserResponse from(User user) {
        return from(user, null);
    }

    public static UserResponse from(User user, String token) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole()); // Map role
        response.setToken(token);
        return response;
    }

}