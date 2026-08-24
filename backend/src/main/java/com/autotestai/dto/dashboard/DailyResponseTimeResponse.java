package com.autotestai.dto.dashboard;

import java.time.LocalDate;

public record DailyResponseTimeResponse(
        LocalDate date,
        long sampleCount,
        Long averageResponseTimeMs) {
}
