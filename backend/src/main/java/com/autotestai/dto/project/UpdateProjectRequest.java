package com.autotestai.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 4000) String description,
        @Size(max = 2048) String baseUrl) {
}
