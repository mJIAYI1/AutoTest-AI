package com.autotestai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.autotestai.entity.DashboardStatisticsEntity;
import com.autotestai.entity.DailyPassRateEntity;
import com.autotestai.entity.DailyResponseTimeEntity;
import com.autotestai.entity.FailingApiStatisticsEntity;
import com.autotestai.entity.RecentFailureEntity;
import com.autotestai.mapper.DashboardMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    private DashboardMapper dashboardMapper;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        dashboardMapper = mock(DashboardMapper.class);
        service = new DashboardService(dashboardMapper);
    }

    @Test
    void buildsOwnedDashboardMetricsAndRecentFailures() {
        DashboardStatisticsEntity statistics = statistics(2, 14, 38, 9, 87, 100);
        RecentFailureEntity failure = new RecentFailureEntity();
        failure.setResultId(51L);
        failure.setRunId(41L);
        failure.setProjectId(7L);
        failure.setProjectName("Order API");
        failure.setApiId(11L);
        failure.setMethod("POST");
        failure.setPath("/orders");
        failure.setApiSummary("Create order");
        failure.setTestCaseId(21L);
        failure.setTestCaseName("Reject zero quantity");
        failure.setStatus("FAIL");
        failure.setResponseStatus(200);
        failure.setResponseTimeMs(125L);
        failure.setExecutedAt(LocalDateTime.now());
        DailyPassRateEntity passRate = new DailyPassRateEntity();
        passRate.setMetricDate(LocalDate.now());
        passRate.setPassedCount(3L);
        passRate.setTotalCount(4L);
        DailyResponseTimeEntity responseTime = new DailyResponseTimeEntity();
        responseTime.setMetricDate(LocalDate.now());
        responseTime.setSampleCount(4L);
        responseTime.setAverageResponseTimeMs(180L);
        FailingApiStatisticsEntity failingApi = new FailingApiStatisticsEntity();
        failingApi.setApiId(11L);
        failingApi.setProjectId(7L);
        failingApi.setProjectName("Order API");
        failingApi.setMethod("POST");
        failingApi.setPath("/orders");
        failingApi.setFailureCount(2L);
        failingApi.setExecutionCount(4L);
        failingApi.setFailureRate(50.0);
        when(dashboardMapper.findStatistics(3)).thenReturn(statistics);
        when(dashboardMapper.findRecentFailures(3, 8)).thenReturn(List.of(failure));
        when(dashboardMapper.findDailyPassRates(3)).thenReturn(List.of(passRate));
        when(dashboardMapper.findDailyResponseTimes(3)).thenReturn(List.of(responseTime));
        when(dashboardMapper.findTopFailingApis(3, 5)).thenReturn(List.of(failingApi));

        var dashboard = service.get(3);

        assertThat(dashboard.recentWindowDays()).isEqualTo(7);
        assertThat(dashboard.projectCount()).isEqualTo(2);
        assertThat(dashboard.apiCount()).isEqualTo(14);
        assertThat(dashboard.testCaseCount()).isEqualTo(38);
        assertThat(dashboard.recentRunCount()).isEqualTo(9);
        assertThat(dashboard.overallPassRate()).isEqualTo(87.0);
        assertThat(dashboard.recentFailures()).singleElement().satisfies(item -> {
            assertThat(item.projectName()).isEqualTo("Order API");
            assertThat(item.path()).isEqualTo("/orders");
            assertThat(item.status()).isEqualTo("FAIL");
        });
        assertThat(dashboard.passRateTrend()).hasSize(7);
        assertThat(dashboard.passRateTrend().get(6).date()).isEqualTo(LocalDate.now());
        assertThat(dashboard.passRateTrend().get(6).passRate()).isEqualTo(75.0);
        assertThat(dashboard.responseTimeTrend()).hasSize(7);
        assertThat(dashboard.responseTimeTrend().get(6).averageResponseTimeMs()).isEqualTo(180L);
        assertThat(dashboard.topFailingApis()).singleElement().satisfies(item -> {
            assertThat(item.path()).isEqualTo("/orders");
            assertThat(item.failureCount()).isEqualTo(2);
            assertThat(item.failureRate()).isEqualTo(50.0);
        });
    }

    @Test
    void returnsZeroPassRateWhenNoCompletedStepsExist() {
        DashboardStatisticsEntity statistics = statistics(0, 0, 0, 0, 0, 0);
        when(dashboardMapper.findStatistics(5)).thenReturn(statistics);
        when(dashboardMapper.findRecentFailures(5, 8)).thenReturn(List.of());
        when(dashboardMapper.findDailyPassRates(5)).thenReturn(List.of());
        when(dashboardMapper.findDailyResponseTimes(5)).thenReturn(List.of());
        when(dashboardMapper.findTopFailingApis(5, 5)).thenReturn(List.of());

        var dashboard = service.get(5);

        assertThat(dashboard.overallPassRate()).isZero();
        assertThat(dashboard.recentFailures()).isEmpty();
        assertThat(dashboard.passRateTrend()).hasSize(7)
                .allSatisfy(point -> assertThat(point.passRate()).isNull());
        assertThat(dashboard.responseTimeTrend()).hasSize(7)
                .allSatisfy(point -> assertThat(point.averageResponseTimeMs()).isNull());
        assertThat(dashboard.topFailingApis()).isEmpty();
    }

    private static DashboardStatisticsEntity statistics(
            long projects,
            long apis,
            long cases,
            long recentRuns,
            long passed,
            long planned) {
        DashboardStatisticsEntity entity = new DashboardStatisticsEntity();
        entity.setProjectCount(projects);
        entity.setApiCount(apis);
        entity.setTestCaseCount(cases);
        entity.setRecentRunCount(recentRuns);
        entity.setTotalPassedCount(passed);
        entity.setTotalPlannedCount(planned);
        return entity;
    }
}
