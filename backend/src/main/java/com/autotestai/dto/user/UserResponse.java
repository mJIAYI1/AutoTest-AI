package com.autotestai.dto.user;

import java.time.LocalDateTime;

import com.autotestai.entity.UserEntity;

public record UserResponse(
        Long id,
        String username,
        String email,
        String displayName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static UserResponse from(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
