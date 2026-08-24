package com.autotestai.dto.testcase;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TestAssertion(
        @NotNull AssertionType type,
        @Size(max = 500) String expression,
        JsonNode expected) {
}
