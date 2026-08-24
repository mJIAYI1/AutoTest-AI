package com.autotestai.dto.dashboard;

import java.time.LocalDate;

public record DailyPassRateResponse(
        LocalDate date,
        long passedCount,
        long totalCount,
        Double passRate) {
}
