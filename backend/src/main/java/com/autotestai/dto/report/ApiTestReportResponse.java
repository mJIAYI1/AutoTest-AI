package com.autotestai.dto.report;

import java.util.List;

import com.autotestai.dto.execution.TestResultResponse;

public record ApiTestReportResponse(
        Long apiId,
        String method,
        String path,
        String summary,
        int totalCount,
        int passedCount,
        int failedCount,
        int errorCount,
        double passRate,
        Long averageResponseTimeMs,
        List<TestResultResponse> results) {
}
