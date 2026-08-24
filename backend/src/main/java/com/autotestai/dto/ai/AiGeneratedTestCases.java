package com.autotestai.dto.ai;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record AiGeneratedTestCases(
        @NotEmpty @Size(max = 12) List<@Valid AiGeneratedTestCase> testCases) {
}
