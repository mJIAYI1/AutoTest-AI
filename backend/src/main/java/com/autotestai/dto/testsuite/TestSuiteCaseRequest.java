package com.autotestai.dto.testsuite;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TestSuiteCaseRequest(
        @NotNull @Positive Long testCaseId,
        @NotNull Boolean enabled) {
}
