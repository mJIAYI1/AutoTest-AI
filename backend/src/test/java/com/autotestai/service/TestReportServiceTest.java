package com.autotestai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autotestai.dto.execution.TestResultResponse;
import com.autotestai.dto.execution.TestRunResponse;
import com.autotestai.entity.ProjectEntity;
import com.autotestai.entity.TestReportSummaryEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.ProjectMapper;
import com.autotestai.mapper.TestRunMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestReportServiceTest {

    private ProjectMapper projectMapper;
    private TestRunMapper testRunMapper;
    private TestRunPersistenceService persistenceService;
    private TestReportService service;

    @BeforeEach
    void setUp() {
        projectMapper = mock(ProjectMapper.class);
        testRunMapper = mock(TestRunMapper.class);
        persistenceService = mock(TestRunPersistenceService.class);
        service = new TestReportService(projectMapper, testRunMapper, persistenceService);

        ProjectEntity project = new ProjectEntity();
        project.setId(7L);
        when(projectMapper.findByIdAndUserId(7, 3)).thenReturn(Optional.of(project));
    }

    @Test
    void listsReportSummariesWithSkippedCountAndPassRate() {
        TestReportSummaryEntity entity = summary(31L, "SUITE", "Purchase flow", null);
        entity.setTotalCount(4);
        entity.setPassedCount(2);
        entity.setFailedCount(1);
        entity.setErrorCount(0);
        entity.setAverageResponseTimeMs(123L);
        when(testRunMapper.findReportSummariesOwned(7, 3, 20)).thenReturn(List.of(entity));

        var reports = service.list(3, 7, 20);

        assertThat(reports).singleElement().satisfies(report -> {
            assertThat(report.title()).isEqualTo("Purchase flow");
            assertThat(report.executedCount()).isEqualTo(3);
            assertThat(report.skippedCount()).isEqualTo(1);
            assertThat(report.passRate()).isEqualTo(50.0);
            assertThat(report.averageResponseTimeMs()).isEqualTo(123L);
        });
        verify(testRunMapper).findReportSummariesOwned(7, 3, 20);
    }

    @Test
    void groupsReportResultsByApiAndCalculatesApiMetrics() {
        TestReportSummaryEntity entity = summary(31L, "SUITE", "Purchase flow", null);
        entity.setTotalCount(3);
        entity.setPassedCount(1);
        entity.setFailedCount(1);
        entity.setErrorCount(1);
        when(testRunMapper.findReportSummaryOwned(31, 7, 3)).thenReturn(Optional.of(entity));

        TestResultResponse passed = result(41, 101, "GET", "/users", "List users", "PASS", 100L);
        TestResultResponse failed = result(42, 101, "GET", "/users", "List users", "FAIL", 200L);
        TestResultResponse error = result(43, 202, "POST", "/orders", "Create order", "ERROR", null);
        TestRunResponse run = new TestRunResponse(
                31L, 7L, null, 9L, 5L, "SUITE", "ERROR", 3, 1, 1, 1,
                "One step failed", LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                null, List.of(passed, failed, error));
        when(persistenceService.get(3, 7, 31)).thenReturn(run);

        var report = service.get(3, 7, 31);

        assertThat(report.summary().executedCount()).isEqualTo(3);
        assertThat(report.apis()).hasSize(2);
        assertThat(report.apis().get(0).apiId()).isEqualTo(101L);
        assertThat(report.apis().get(0).results()).containsExactly(passed, failed);
        assertThat(report.apis().get(0).passRate()).isEqualTo(50.0);
        assertThat(report.apis().get(0).averageResponseTimeMs()).isEqualTo(150L);
        assertThat(report.apis().get(1).apiId()).isEqualTo(202L);
        assertThat(report.apis().get(1).errorCount()).isEqualTo(1);
        assertThat(report.apis().get(1).averageResponseTimeMs()).isNull();
    }

    @Test
    void hidesReportsOutsideTheOwnedProject() {
        when(testRunMapper.findReportSummaryOwned(99, 7, 3)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(3, 7, 99))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("TEST_REPORT_NOT_FOUND"));
    }

    private static TestReportSummaryEntity summary(
            long id, String runType, String suiteName, String testCaseName) {
        TestReportSummaryEntity entity = new TestReportSummaryEntity();
        entity.setId(id);
        entity.setProjectId(7L);
        entity.setTestSuiteId("SUITE".equals(runType) ? 9L : null);
        entity.setTestSuiteName(suiteName);
        entity.setTargetTestCaseId("SINGLE_CASE".equals(runType) ? 11L : null);
        entity.setTestCaseName(testCaseName);
        entity.setEnvironmentId(5L);
        entity.setEnvironmentName("Local");
        entity.setRunType(runType);
        entity.setStatus("FAIL");
        entity.setErrorMessage(null);
        entity.setStartedAt(LocalDateTime.now());
        entity.setFinishedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private static TestResultResponse result(
            long id,
            long apiId,
            String method,
            String path,
            String summary,
            String status,
            Long responseTimeMs) {
        return new TestResultResponse(
                id, id + 100, apiId, method, path, summary, (int) id, "Case " + id,
                status, "http://localhost" + path, method, Map.of(), null,
                responseTimeMs == null ? null : 200, Map.of(), "{}", responseTimeMs,
                List.of(), List.of(), "ERROR".equals(status) ? "Connection failed" : null,
                LocalDateTime.now());
    }
}
