package com.autotestai.dto.environment;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEnvironmentRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 2048) String baseUrl,
        @Size(max = 100) Map<String, String> headers,
        @Size(max = 100) Map<String, String> variables) {
}
