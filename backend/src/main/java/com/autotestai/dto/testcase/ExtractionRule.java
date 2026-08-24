package com.autotestai.dto.testcase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExtractionRule(
        @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "^[A-Za-z_][A-Za-z0-9_.-]*$", message = "must be a template-safe variable name")
        String name,

        @NotBlank
        @Size(max = 500)
        String jsonPath) {
}
