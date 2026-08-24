package com.autotestai.entity;

public class TestSuiteCaseEntity {

    private Long testSuiteId;
    private Long testCaseId;
    private Integer sortOrder;
    private Boolean enabled;
    private String testCaseName;
    private Boolean testCaseEnabled;
    private Long apiId;
    private String method;
    private String path;

    public Long getTestSuiteId() { return testSuiteId; }
    public void setTestSuiteId(Long testSuiteId) { this.testSuiteId = testSuiteId; }
    public Long getTestCaseId() { return testCaseId; }
    public void setTestCaseId(Long testCaseId) { this.testCaseId = testCaseId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getTestCaseName() { return testCaseName; }
    public void setTestCaseName(String testCaseName) { this.testCaseName = testCaseName; }
    public Boolean getTestCaseEnabled() { return testCaseEnabled; }
    public void setTestCaseEnabled(Boolean testCaseEnabled) { this.testCaseEnabled = testCaseEnabled; }
    public Long getApiId() { return apiId; }
    public void setApiId(Long apiId) { this.apiId = apiId; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}
