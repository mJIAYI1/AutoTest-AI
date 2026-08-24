package com.autotestai.controller;

import com.autotestai.dto.execution.RunTestCaseRequest;
import com.autotestai.dto.execution.TestRunResponse;
import com.autotestai.security.CurrentUserId;
import com.autotestai.service.TestRunOrchestrator;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestRunController {

    private final TestRunOrchestrator testRunOrchestrator;

    public TestRunController(TestRunOrchestrator testRunOrchestrator) {
        this.testRunOrchestrator = testRunOrchestrator;
    }

    @PostMapping("/api/projects/{projectId}/apis/{apiId}/test-cases/{testCaseId}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TestRunResponse runSingle(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long apiId,
            @PathVariable long testCaseId,
            @Valid @RequestBody RunTestCaseRequest request) {
        return testRunOrchestrator.startSingle(
                CurrentUserId.from(jwt),
                projectId,
                apiId,
                testCaseId,
                request.environmentId());
    }

    @PostMapping("/api/projects/{projectId}/test-suites/{suiteId}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TestRunResponse runSuite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long suiteId,
            @Valid @RequestBody RunTestCaseRequest request) {
        return testRunOrchestrator.startSuite(
                CurrentUserId.from(jwt),
                projectId,
                suiteId,
                request.environmentId());
    }

    @GetMapping("/api/projects/{projectId}/test-runs/{runId}")
    public TestRunResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long runId) {
        return testRunOrchestrator.get(CurrentUserId.from(jwt), projectId, runId);
    }
}
