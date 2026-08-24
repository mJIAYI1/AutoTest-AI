package com.autotestai;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformStatusController {

    @GetMapping("/")
    public Map<String, String> status() {
        return Map.ofEntries(
                Map.entry("application", "AutoTest AI"),
                Map.entry("status", "UP"),
                Map.entry("stage", "release-candidate"),
                Map.entry("health", "/actuator/health"),
                Map.entry("register", "/api/auth/register"),
                Map.entry("login", "/api/auth/login"),
                Map.entry("currentUser", "/api/users/me"),
                Map.entry("projects", "/api/projects"),
                Map.entry("dashboard", "/api/dashboard/summary"),
                Map.entry("environments", "/api/projects/{projectId}/environments"),
                Map.entry("apis", "/api/projects/{projectId}/apis"),
                Map.entry("testCases", "/api/projects/{projectId}/apis/{apiId}/test-cases"),
                Map.entry("testSuites", "/api/projects/{projectId}/test-suites"),
                Map.entry("runTestCase", "/api/projects/{projectId}/apis/{apiId}/test-cases/{testCaseId}/runs"),
                Map.entry("runTestSuite", "/api/projects/{projectId}/test-suites/{suiteId}/runs"),
                Map.entry("testRun", "/api/projects/{projectId}/test-runs/{runId}"),
                Map.entry("testReports", "/api/projects/{projectId}/test-reports"),
                Map.entry("aiStatus", "/api/ai/status"),
                Map.entry("aiGenerate", "/api/projects/{projectId}/apis/{apiId}/ai/test-cases/generate"),
                Map.entry("aiDiagnosis", "/api/projects/{projectId}/test-runs/{runId}/results/{resultId}/ai/diagnosis"));
    }
}
