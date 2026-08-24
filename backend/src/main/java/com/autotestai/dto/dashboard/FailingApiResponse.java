package com.autotestai.dto.dashboard;

public record FailingApiResponse(
        Long apiId,
        Long projectId,
        String projectName,
        String method,
        String path,
        String summary,
        long failureCount,
        long executionCount,
        double failureRate) {
}
