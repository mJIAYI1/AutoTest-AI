package com.autotestai.dto.openapi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OpenApiUrlImportRequest(
        @NotBlank @Size(max = 2048) String url) {
}
