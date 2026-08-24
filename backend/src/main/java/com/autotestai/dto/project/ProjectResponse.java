package com.autotestai.dto.project;

import java.time.LocalDateTime;

import com.autotestai.entity.ProjectEntity;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        String baseUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProjectResponse from(ProjectEntity project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getBaseUrl(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
