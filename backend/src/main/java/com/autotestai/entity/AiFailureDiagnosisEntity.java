package com.autotestai.entity;

import java.time.LocalDateTime;

public class AiFailureDiagnosisEntity {

    private Long id;
    private Long testResultId;
    private String provider;
    private String model;
    private String summary;
    private String severity;
    private String possibleCausesJson;
    private String checkLocationsJson;
    private String repairSuggestionsJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTestResultId() { return testResultId; }
    public void setTestResultId(Long testResultId) { this.testResultId = testResultId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getPossibleCausesJson() { return possibleCausesJson; }
    public void setPossibleCausesJson(String possibleCausesJson) { this.possibleCausesJson = possibleCausesJson; }
    public String getCheckLocationsJson() { return checkLocationsJson; }
    public void setCheckLocationsJson(String checkLocationsJson) { this.checkLocationsJson = checkLocationsJson; }
    public String getRepairSuggestionsJson() { return repairSuggestionsJson; }
    public void setRepairSuggestionsJson(String repairSuggestionsJson) { this.repairSuggestionsJson = repairSuggestionsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
