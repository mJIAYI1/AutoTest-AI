package com.autotestai.dto.execution;

import com.autotestai.dto.testcase.AssertionType;
import com.fasterxml.jackson.databind.JsonNode;

public record AssertionResult(
        AssertionType type,
        String expression,
        JsonNode expected,
        JsonNode actual,
        boolean passed,
        String message) {
}
