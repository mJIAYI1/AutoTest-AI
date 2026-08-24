package com.autotestai.entity;

import java.time.LocalDate;

public class DailyResponseTimeEntity {

    private LocalDate metricDate;
    private Long sampleCount;
    private Long averageResponseTimeMs;

    public LocalDate getMetricDate() { return metricDate; }
    public void setMetricDate(LocalDate metricDate) { this.metricDate = metricDate; }
    public Long getSampleCount() { return sampleCount; }
    public void setSampleCount(Long sampleCount) { this.sampleCount = sampleCount; }
    public Long getAverageResponseTimeMs() { return averageResponseTimeMs; }
    public void setAverageResponseTimeMs(Long averageResponseTimeMs) { this.averageResponseTimeMs = averageResponseTimeMs; }
}
