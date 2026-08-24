package com.autotestai.dto.report;

import java.util.List;

import com.autotestai.dto.execution.TestRunResponse;

public record TestReportDetailResponse(
        TestReportSummaryResponse summary,
        TestRunResponse run,
        List<ApiTestReportResponse> apis) {
}
