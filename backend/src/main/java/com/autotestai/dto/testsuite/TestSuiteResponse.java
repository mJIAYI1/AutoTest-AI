package com.autotestai.dto.testsuite;

import java.time.LocalDateTime;
import java.util.List;

public record TestSuiteResponse(
        Long id,
        Long projectId,
        String name,
        String description,
        boolean stopOnFailure,
        String status,
        int version,
        List<TestSuiteCaseResponse> cases,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
