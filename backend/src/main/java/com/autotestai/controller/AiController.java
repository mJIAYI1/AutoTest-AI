package com.autotestai.controller;

import com.autotestai.dto.ai.AiProviderStatusResponse;
import com.autotestai.dto.ai.AiFailureDiagnosisResponse;
import com.autotestai.dto.ai.AiTestCaseGenerationRequest;
import com.autotestai.dto.ai.AiTestCaseGenerationResponse;
import com.autotestai.security.CurrentUserId;
import com.autotestai.service.AiService;
import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/api/ai/status")
    public AiProviderStatusResponse status() {
        return aiService.status();
    }

    @PostMapping("/api/projects/{projectId}/apis/{apiId}/ai/test-cases/generate")
    public AiTestCaseGenerationResponse generateTestCases(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long apiId,
            @Valid @RequestBody AiTestCaseGenerationRequest request) {
        return aiService.generateTestCases(
                CurrentUserId.from(jwt),
                projectId,
                apiId,
                request);
    }

    @GetMapping("/api/projects/{projectId}/test-runs/{runId}/results/{resultId}/ai/diagnosis")
    public AiFailureDiagnosisResponse getFailureDiagnosis(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long runId,
            @PathVariable long resultId) {
        return aiService.getFailureDiagnosis(
                CurrentUserId.from(jwt), projectId, runId, resultId);
    }

    @PostMapping("/api/projects/{projectId}/test-runs/{runId}/results/{resultId}/ai/diagnosis")
    public AiFailureDiagnosisResponse analyzeFailure(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long runId,
            @PathVariable long resultId) {
        return aiService.analyzeFailure(
                CurrentUserId.from(jwt), projectId, runId, resultId);
    }
}
