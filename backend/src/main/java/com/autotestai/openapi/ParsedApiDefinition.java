package com.autotestai.openapi;

public record ParsedApiDefinition(
        String operationId,
        String method,
        String path,
        String summary,
        String description,
        String tagsJson,
        String parametersJson,
        String requestSchemaJson,
        String responseSchemaJson,
        String securityJson) {
}
