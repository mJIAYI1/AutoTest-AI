package com.autotestai.dto.report;

import java.time.LocalDateTime;

public record TestReportSummaryResponse(
        Long id,
        Long projectId,
        String title,
        String runType,
        Long testSuiteId,
        String testSuiteName,
        Long testCaseId,
        String testCaseName,
        Long environmentId,
        String environmentName,
        String status,
        int totalCount,
        int executedCount,
        int skippedCount,
        int passedCount,
        int failedCount,
        int errorCount,
        double passRate,
        Long averageResponseTimeMs,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt) {
}
