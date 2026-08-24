package com.autotestai.dto.execution;

import jakarta.validation.constraints.Positive;

public record RunTestCaseRequest(@Positive Long environmentId) {
}
