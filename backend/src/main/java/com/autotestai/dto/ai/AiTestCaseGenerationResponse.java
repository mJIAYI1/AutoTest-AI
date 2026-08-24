package com.autotestai.dto.ai;

import java.time.LocalDateTime;
import java.util.List;

public record AiTestCaseGenerationResponse(
        long apiId,
        String provider,
        String model,
        List<AiGeneratedTestCase> candidates,
        List<String> warnings,
        LocalDateTime generatedAt) {
}
