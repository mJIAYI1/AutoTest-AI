package com.autotestai.dto.dashboard;

import java.time.LocalDateTime;

public record RecentFailureResponse(
        Long resultId,
        Long runId,
        Long projectId,
        String projectName,
        Long apiId,
        String method,
        String path,
        String apiSummary,
        Long testCaseId,
        String testCaseName,
        String status,
        Integer responseStatus,
        Long responseTimeMs,
        String errorMessage,
        LocalDateTime executedAt) {
}
