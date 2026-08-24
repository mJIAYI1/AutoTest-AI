package com.autotestai.dto.openapi;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public record ApiDefinitionResponse(
        long id,
        long projectId,
        String operationId,
        String method,
        String path,
        String summary,
        String description,
        List<String> tags,
        JsonNode parameters,
        JsonNode requestSchema,
        JsonNode responseSchema,
        JsonNode security,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
