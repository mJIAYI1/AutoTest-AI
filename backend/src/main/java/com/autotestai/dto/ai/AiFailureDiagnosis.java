package com.autotestai.dto.ai;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiFailureDiagnosis(
        @NotBlank @Size(max = 500) String summary,
        @NotNull AiDiagnosisSeverity severity,
        @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 500) String> possibleCauses,
        @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 500) String> checkLocations,
        @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 1000) String> repairSuggestions) {
}
