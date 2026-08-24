package com.autotestai.dto.ai;

import java.time.LocalDateTime;

public record AiFailureDiagnosisResponse(
        Long id,
        Long testRunId,
        Long testResultId,
        String provider,
        String model,
        AiFailureDiagnosis diagnosis,
        LocalDateTime generatedAt) {
}
