package com.autotestai.entity;

import java.time.LocalDateTime;

public class TestCaseEntity {

    private Long id;
    private Long apiId;
    private String name;
    private String description;
    private String type;
    private String requestHeadersJson;
    private String pathParametersJson;
    private String queryParametersJson;
    private String requestBodyJson;
    private String assertionsJson;
    private String extractionRulesJson;
    private Boolean enabled;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApiId() {
        return apiId;
    }

    public void setApiId(Long apiId) {
        this.apiId = apiId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRequestHeadersJson() {
        return requestHeadersJson;
    }

    public void setRequestHeadersJson(String requestHeadersJson) {
        this.requestHeadersJson = requestHeadersJson;
    }

    public String getPathParametersJson() {
        return pathParametersJson;
    }

    public void setPathParametersJson(String pathParametersJson) {
        this.pathParametersJson = pathParametersJson;
    }

    public String getQueryParametersJson() {
        return queryParametersJson;
    }

    public void setQueryParametersJson(String queryParametersJson) {
        this.queryParametersJson = queryParametersJson;
    }

    public String getRequestBodyJson() {
        return requestBodyJson;
    }

    public void setRequestBodyJson(String requestBodyJson) {
        this.requestBodyJson = requestBodyJson;
    }

    public String getAssertionsJson() {
        return assertionsJson;
    }

    public void setAssertionsJson(String assertionsJson) {
        this.assertionsJson = assertionsJson;
    }

    public String getExtractionRulesJson() {
        return extractionRulesJson;
    }

    public void setExtractionRulesJson(String extractionRulesJson) {
        this.extractionRulesJson = extractionRulesJson;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
