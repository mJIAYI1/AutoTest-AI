package com.autotestai.dto.environment;

import java.time.LocalDateTime;
import java.util.Map;

public record EnvironmentResponse(
        Long id,
        Long projectId,
        String name,
        String baseUrl,
        Map<String, String> headers,
        Map<String, String> variables,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
