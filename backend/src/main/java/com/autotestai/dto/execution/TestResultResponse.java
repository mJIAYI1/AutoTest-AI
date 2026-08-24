package com.autotestai.dto.execution;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record TestResultResponse(
        Long id,
        Long testCaseId,
        Long apiId,
        String apiMethod,
        String apiPath,
        String apiSummary,
        int sequenceNumber,
        String testCaseName,
        String status,
        String requestUrl,
        String requestMethod,
        Map<String, List<String>> requestHeaders,
        String requestBody,
        Integer responseStatus,
        Map<String, List<String>> responseHeaders,
        String responseBody,
        Long responseTimeMs,
        List<AssertionResult> assertions,
        List<ExtractedValue> extractedVariables,
        String errorMessage,
        LocalDateTime executedAt) {
}
