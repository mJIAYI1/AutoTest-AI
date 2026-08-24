package com.autotestai.dto.testcase;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTestCaseRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @NotNull TestCaseType type,
        @Size(max = 100) Map<String, String> requestHeaders,
        @Size(max = 100) Map<String, String> pathParameters,
        @Size(max = 100) Map<String, String> queryParameters,
        JsonNode requestBody,
        @NotEmpty @Size(max = 50) List<@Valid TestAssertion> assertions,
        @Size(max = 50) List<@Valid ExtractionRule> extractionRules,
        @NotNull Boolean enabled,
        @Min(1) int version) {
}
