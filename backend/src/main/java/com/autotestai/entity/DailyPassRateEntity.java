package com.autotestai.entity;

import java.time.LocalDate;

public class DailyPassRateEntity {

    private LocalDate metricDate;
    private Long passedCount;
    private Long totalCount;

    public LocalDate getMetricDate() { return metricDate; }
    public void setMetricDate(LocalDate metricDate) { this.metricDate = metricDate; }
    public Long getPassedCount() { return passedCount; }
    public void setPassedCount(Long passedCount) { this.passedCount = passedCount; }
    public Long getTotalCount() { return totalCount; }
    public void setTotalCount(Long totalCount) { this.totalCount = totalCount; }
}
