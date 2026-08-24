package com.autotestai.dto.execution;

import java.time.LocalDateTime;
import java.util.List;

public record TestRunResponse(
        Long id,
        Long projectId,
        Long testCaseId,
        Long testSuiteId,
        Long environmentId,
        String runType,
        String status,
        int totalCount,
        int passedCount,
        int failedCount,
        int errorCount,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        TestResultResponse result,
        List<TestResultResponse> results) {
}
