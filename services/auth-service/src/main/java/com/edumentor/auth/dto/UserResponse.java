package com.edumentor.auth.dto;

import com.edumentor.auth.entity.Role;
import com.edumentor.auth.entity.User;

import java.time.LocalDateTime;

public record UserResponse(Long id, String name, String email, Role role, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}