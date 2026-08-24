package com.autotestai.dto.testcase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public record TestCaseResponse(
        long id,
        long apiId,
        String name,
        String description,
        TestCaseType type,
        Map<String, String> requestHeaders,
        Map<String, String> pathParameters,
        Map<String, String> queryParameters,
        JsonNode requestBody,
        List<TestAssertion> assertions,
        List<ExtractionRule> extractionRules,
        boolean enabled,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
