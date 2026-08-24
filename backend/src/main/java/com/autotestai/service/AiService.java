package com.autotestai.service;

import com.autotestai.dto.ai.AiProviderStatusResponse;
import com.autotestai.dto.ai.AiFailureDiagnosisResponse;
import com.autotestai.dto.ai.AiTestCaseGenerationRequest;
import com.autotestai.dto.ai.AiTestCaseGenerationResponse;

public interface AiService {

    AiProviderStatusResponse status();

    AiTestCaseGenerationResponse generateTestCases(
            long userId,
            long projectId,
            long apiId,
            AiTestCaseGenerationRequest request);

    AiFailureDiagnosisResponse getFailureDiagnosis(
            long userId,
            long projectId,
            long runId,
            long resultId);

    AiFailureDiagnosisResponse analyzeFailure(
            long userId,
            long projectId,
            long runId,
            long resultId);
}
