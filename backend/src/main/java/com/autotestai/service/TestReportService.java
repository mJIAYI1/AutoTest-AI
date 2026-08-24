package com.autotestai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.autotestai.dto.execution.TestResultResponse;
import com.autotestai.dto.execution.TestRunResponse;
import com.autotestai.dto.report.ApiTestReportResponse;
import com.autotestai.dto.report.TestReportDetailResponse;
import com.autotestai.dto.report.TestReportSummaryResponse;
import com.autotestai.entity.TestReportSummaryEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.ProjectMapper;
import com.autotestai.mapper.TestRunMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestReportService {

    private final ProjectMapper projectMapper;
    private final TestRunMapper testRunMapper;
    private final TestRunPersistenceService testRunPersistenceService;

    public TestReportService(
            ProjectMapper projectMapper,
            TestRunMapper testRunMapper,
            TestRunPersistenceService testRunPersistenceService) {
        this.projectMapper = projectMapper;
        this.testRunMapper = testRunMapper;
        this.testRunPersistenceService = testRunPersistenceService;
    }

    @Transactional(readOnly = true)
    public List<TestReportSummaryResponse> list(long userId, long projectId, int limit) {
        if (projectMapper.findByIdAndUserId(projectId, userId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project was not found");
        }
        return testRunMapper.findReportSummariesOwned(projectId, userId, limit).stream()
                .map(TestReportService::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public TestReportDetailResponse get(long userId, long projectId, long runId) {
        TestReportSummaryEntity summaryEntity = testRunMapper.findReportSummaryOwned(
                        runId, projectId, userId)
                .orElseThrow(TestReportService::reportNotFound);
        TestRunResponse run = testRunPersistenceService.get(userId, projectId, runId);
        Map<Long, List<TestResultResponse>> grouped = new LinkedHashMap<>();
        run.results().forEach(result -> grouped
                .computeIfAbsent(result.apiId(), ignored -> new ArrayList<>())
                .add(result));
        List<ApiTestReportResponse> apis = grouped.values().stream()
                .map(TestReportService::toApiReport)
                .toList();
        return new TestReportDetailResponse(toSummary(summaryEntity), run, apis);
    }

    private static TestReportSummaryResponse toSummary(TestReportSummaryEntity entity) {
        int total = valueOrZero(entity.getTotalCount());
        int passed = valueOrZero(entity.getPassedCount());
        int failed = valueOrZero(entity.getFailedCount());
        int errors = valueOrZero(entity.getErrorCount());
        int executed = passed + failed + errors;
        int skipped = Math.max(0, total - executed);
        String title = "SUITE".equals(entity.getRunType())
                ? defaultTitle(entity.getTestSuiteName(), "Test Suite", entity.getId())
                : defaultTitle(entity.getTestCaseName(), "Test Case", entity.getId());
        return new TestReportSummaryResponse(
                entity.getId(),
                entity.getProjectId(),
                title,
                entity.getRunType(),
                entity.getTestSuiteId(),
                entity.getTestSuiteName(),
                entity.getTargetTestCaseId(),
                entity.getTestCaseName(),
                entity.getEnvironmentId(),
                entity.getEnvironmentName(),
                entity.getStatus(),
                total,
                executed,
                skipped,
                passed,
                failed,
                errors,
                percentage(passed, total),
                entity.getAverageResponseTimeMs(),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt());
    }

    private static ApiTestReportResponse toApiReport(List<TestResultResponse> results) {
        TestResultResponse first = results.get(0);
        int passed = (int) results.stream().filter(result -> "PASS".equals(result.status())).count();
        int failed = (int) results.stream().filter(result -> "FAIL".equals(result.status())).count();
        int errors = (int) results.stream().filter(result -> "ERROR".equals(result.status())).count();
        List<Long> responseTimes = results.stream()
                .map(TestResultResponse::responseTimeMs)
                .filter(value -> value != null)
                .toList();
        Long average = responseTimes.isEmpty()
                ? null
                : Math.round(responseTimes.stream().mapToLong(Long::longValue).average().orElse(0));
        return new ApiTestReportResponse(
                first.apiId(),
                first.apiMethod(),
                first.apiPath(),
                first.apiSummary(),
                results.size(),
                passed,
                failed,
                errors,
                percentage(passed, results.size()),
                average,
                List.copyOf(results));
    }

    private static double percentage(int passed, int total) {
        if (total <= 0) return 0.0;
        return Math.round((passed * 10_000.0) / total) / 100.0;
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String defaultTitle(String value, String prefix, long id) {
        return value == null || value.isBlank() ? prefix + " Run #" + id : value;
    }

    private static ApiException reportNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TEST_REPORT_NOT_FOUND", "Test report was not found");
    }
}
