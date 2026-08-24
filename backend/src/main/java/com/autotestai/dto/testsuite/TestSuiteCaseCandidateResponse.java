package com.autotestai.dto.testsuite;

public record TestSuiteCaseCandidateResponse(
        Long testCaseId,
        String testCaseName,
        boolean enabled,
        Long apiId,
        String method,
        String path) {
}
