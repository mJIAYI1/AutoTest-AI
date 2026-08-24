package com.autotestai.controller;

import java.util.List;

import com.autotestai.dto.report.TestReportDetailResponse;
import com.autotestai.dto.report.TestReportSummaryResponse;
import com.autotestai.security.CurrentUserId;
import com.autotestai.service.TestReportService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class TestReportController {

    private final TestReportService testReportService;

    public TestReportController(TestReportService testReportService) {
        this.testReportService = testReportService;
    }

    @GetMapping("/api/projects/{projectId}/test-reports")
    public List<TestReportSummaryResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        return testReportService.list(CurrentUserId.from(jwt), projectId, limit);
    }

    @GetMapping("/api/projects/{projectId}/test-reports/{runId}")
    public TestReportDetailResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long runId) {
        return testReportService.get(CurrentUserId.from(jwt), projectId, runId);
    }
}
