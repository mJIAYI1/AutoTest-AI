package com.autotestai.ai;

import com.autotestai.dto.ai.AiGeneratedTestCases;
import com.autotestai.dto.ai.AiFailureDiagnosis;

public interface AiModelGateway {

    boolean isConfigured();

    String provider();

    String model();

    AiGeneratedTestCases generateTestCases(String systemPrompt, String userPrompt);

    AiFailureDiagnosis analyzeFailure(String systemPrompt, String userPrompt);
}
