package com.autotestai.dto.dashboard;

import java.util.List;

public record DashboardSummaryResponse(
        int recentWindowDays,
        long projectCount,
        long apiCount,
        long testCaseCount,
        long recentRunCount,
        double overallPassRate,
        List<RecentFailureResponse> recentFailures,
        List<DailyPassRateResponse> passRateTrend,
        List<DailyResponseTimeResponse> responseTimeTrend,
        List<FailingApiResponse> topFailingApis) {
}
