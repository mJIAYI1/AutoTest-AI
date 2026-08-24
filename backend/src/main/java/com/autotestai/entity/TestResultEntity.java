package com.autotestai.entity;

import java.time.LocalDateTime;

public class TestResultEntity {

    private Long id;
    private Long testRunId;
    private Long testCaseId;
    private Long apiId;
    private String apiMethod;
    private String apiPath;
    private String apiSummary;
    private Integer sequenceNumber;
    private String testCaseName;
    private String status;
    private String requestUrl;
    private String requestMethod;
    private String requestHeadersJson;
    private String requestBody;
    private Integer responseStatus;
    private String responseHeadersJson;
    private String responseBody;
    private Long responseTimeMs;
    private String assertionResultsJson;
    private String errorMessage;
    private LocalDateTime executedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTestRunId() { return testRunId; }
    public void setTestRunId(Long testRunId) { this.testRunId = testRunId; }
    public Long getTestCaseId() { return testCaseId; }
    public void setTestCaseId(Long testCaseId) { this.testCaseId = testCaseId; }
    public Long getApiId() { return apiId; }
    public void setApiId(Long apiId) { this.apiId = apiId; }
    public String getApiMethod() { return apiMethod; }
    public void setApiMethod(String apiMethod) { this.apiMethod = apiMethod; }
    public String getApiPath() { return apiPath; }
    public void setApiPath(String apiPath) { this.apiPath = apiPath; }
    public String getApiSummary() { return apiSummary; }
    public void setApiSummary(String apiSummary) { this.apiSummary = apiSummary; }
    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public String getTestCaseName() { return testCaseName; }
    public void setTestCaseName(String testCaseName) { this.testCaseName = testCaseName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }
    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    public String getRequestHeadersJson() { return requestHeadersJson; }
    public void setRequestHeadersJson(String requestHeadersJson) { this.requestHeadersJson = requestHeadersJson; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }
    public String getResponseHeadersJson() { return responseHeadersJson; }
    public void setResponseHeadersJson(String responseHeadersJson) { this.responseHeadersJson = responseHeadersJson; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public Long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Long responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    public String getAssertionResultsJson() { return assertionResultsJson; }
    public void setAssertionResultsJson(String assertionResultsJson) { this.assertionResultsJson = assertionResultsJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
}
