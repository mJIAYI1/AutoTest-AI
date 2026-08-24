package com.autotestai.entity;

public class DashboardStatisticsEntity {

    private Long projectCount;
    private Long apiCount;
    private Long testCaseCount;
    private Long recentRunCount;
    private Long totalPassedCount;
    private Long totalPlannedCount;

    public Long getProjectCount() { return projectCount; }
    public void setProjectCount(Long projectCount) { this.projectCount = projectCount; }
    public Long getApiCount() { return apiCount; }
    public void setApiCount(Long apiCount) { this.apiCount = apiCount; }
    public Long getTestCaseCount() { return testCaseCount; }
    public void setTestCaseCount(Long testCaseCount) { this.testCaseCount = testCaseCount; }
    public Long getRecentRunCount() { return recentRunCount; }
    public void setRecentRunCount(Long recentRunCount) { this.recentRunCount = recentRunCount; }
    public Long getTotalPassedCount() { return totalPassedCount; }
    public void setTotalPassedCount(Long totalPassedCount) { this.totalPassedCount = totalPassedCount; }
    public Long getTotalPlannedCount() { return totalPlannedCount; }
    public void setTotalPlannedCount(Long totalPlannedCount) { this.totalPlannedCount = totalPlannedCount; }
}
