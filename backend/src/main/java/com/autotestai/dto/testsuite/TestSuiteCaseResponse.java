package com.autotestai.dto.testsuite;

public record TestSuiteCaseResponse(
        Long testCaseId,
        int sortOrder,
        boolean enabled,
        String testCaseName,
        boolean testCaseEnabled,
        Long apiId,
        String method,
        String path) {
}
