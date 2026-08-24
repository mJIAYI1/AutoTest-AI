package com.autotestai.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AiTestCaseGenerationRequest(
        @Min(1) @Max(12) Integer count,
        @Size(max = 1000) String focus) {

    public int resolvedCount() {
        return count == null ? 6 : count;
    }
}
