package com.autotestai.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.autotestai.dto.dashboard.DailyPassRateResponse;
import com.autotestai.dto.dashboard.DailyResponseTimeResponse;
import com.autotestai.dto.dashboard.DashboardSummaryResponse;
import com.autotestai.dto.dashboard.FailingApiResponse;
import com.autotestai.dto.dashboard.RecentFailureResponse;
import com.autotestai.entity.DailyPassRateEntity;
import com.autotestai.entity.DailyResponseTimeEntity;
import com.autotestai.entity.DashboardStatisticsEntity;
import com.autotestai.entity.FailingApiStatisticsEntity;
import com.autotestai.entity.RecentFailureEntity;
import com.autotestai.mapper.DashboardMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    static final int RECENT_WINDOW_DAYS = 7;
    static final int RECENT_FAILURE_LIMIT = 8;
    static final int TOP_FAILING_API_LIMIT = 5;

    private final DashboardMapper dashboardMapper;

    public DashboardService(DashboardMapper dashboardMapper) {
        this.dashboardMapper = dashboardMapper;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse get(long userId) {
        DashboardStatisticsEntity statistics = dashboardMapper.findStatistics(userId);
        List<RecentFailureResponse> failures = dashboardMapper
                .findRecentFailures(userId, RECENT_FAILURE_LIMIT)
                .stream()
                .map(DashboardService::toFailure)
                .toList();
        List<DailyPassRateResponse> passRateTrend = fillPassRateTrend(
                dashboardMapper.findDailyPassRates(userId));
        List<DailyResponseTimeResponse> responseTimeTrend = fillResponseTimeTrend(
                dashboardMapper.findDailyResponseTimes(userId));
        List<FailingApiResponse> topFailingApis = dashboardMapper
                .findTopFailingApis(userId, TOP_FAILING_API_LIMIT)
                .stream()
                .map(DashboardService::toFailingApi)
                .toList();
        long passed = valueOrZero(statistics.getTotalPassedCount());
        long planned = valueOrZero(statistics.getTotalPlannedCount());
        return new DashboardSummaryResponse(
                RECENT_WINDOW_DAYS,
                valueOrZero(statistics.getProjectCount()),
                valueOrZero(statistics.getApiCount()),
                valueOrZero(statistics.getTestCaseCount()),
                valueOrZero(statistics.getRecentRunCount()),
                percentage(passed, planned),
                failures,
                passRateTrend,
                responseTimeTrend,
                topFailingApis);
    }

    private static RecentFailureResponse toFailure(RecentFailureEntity entity) {
        return new RecentFailureResponse(
                entity.getResultId(),
                entity.getRunId(),
                entity.getProjectId(),
                entity.getProjectName(),
                entity.getApiId(),
                entity.getMethod(),
                entity.getPath(),
                entity.getApiSummary(),
                entity.getTestCaseId(),
                entity.getTestCaseName(),
                entity.getStatus(),
                entity.getResponseStatus(),
                entity.getResponseTimeMs(),
                entity.getErrorMessage(),
                entity.getExecutedAt());
    }

    private static List<DailyPassRateResponse> fillPassRateTrend(List<DailyPassRateEntity> entities) {
        Map<LocalDate, DailyPassRateEntity> byDate = entities.stream()
                .collect(Collectors.toMap(DailyPassRateEntity::getMetricDate, Function.identity()));
        LocalDate firstDate = LocalDate.now().minusDays(RECENT_WINDOW_DAYS - 1L);
        return IntStream.range(0, RECENT_WINDOW_DAYS)
                .mapToObj(offset -> {
                    LocalDate date = firstDate.plusDays(offset);
                    DailyPassRateEntity entity = byDate.get(date);
                    long passed = entity == null ? 0 : valueOrZero(entity.getPassedCount());
                    long total = entity == null ? 0 : valueOrZero(entity.getTotalCount());
                    return new DailyPassRateResponse(
                            date,
                            passed,
                            total,
                            total == 0 ? null : percentage(passed, total));
                })
                .toList();
    }

    private static List<DailyResponseTimeResponse> fillResponseTimeTrend(
            List<DailyResponseTimeEntity> entities) {
        Map<LocalDate, DailyResponseTimeEntity> byDate = entities.stream()
                .collect(Collectors.toMap(DailyResponseTimeEntity::getMetricDate, Function.identity()));
        LocalDate firstDate = LocalDate.now().minusDays(RECENT_WINDOW_DAYS - 1L);
        return IntStream.range(0, RECENT_WINDOW_DAYS)
                .mapToObj(offset -> {
                    LocalDate date = firstDate.plusDays(offset);
                    DailyResponseTimeEntity entity = byDate.get(date);
                    return new DailyResponseTimeResponse(
                            date,
                            entity == null ? 0 : valueOrZero(entity.getSampleCount()),
                            entity == null ? null : entity.getAverageResponseTimeMs());
                })
                .toList();
    }

    private static FailingApiResponse toFailingApi(FailingApiStatisticsEntity entity) {
        return new FailingApiResponse(
                entity.getApiId(),
                entity.getProjectId(),
                entity.getProjectName(),
                entity.getMethod(),
                entity.getPath(),
                entity.getSummary(),
                valueOrZero(entity.getFailureCount()),
                valueOrZero(entity.getExecutionCount()),
                entity.getFailureRate() == null ? 0.0 : entity.getFailureRate());
    }

    private static double percentage(long passed, long planned) {
        if (planned <= 0) return 0.0;
        return Math.round((passed * 10_000.0) / planned) / 100.0;
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }
}
