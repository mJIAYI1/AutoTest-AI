package com.autotestai.entity;

public class ExtractedVariableEntity {

    private Long id;
    private Long testRunId;
    private Long testResultId;
    private String name;
    private String valueText;
    private String sourceExpression;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTestRunId() { return testRunId; }
    public void setTestRunId(Long testRunId) { this.testRunId = testRunId; }
    public Long getTestResultId() { return testResultId; }
    public void setTestResultId(Long testResultId) { this.testResultId = testResultId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }
    public String getSourceExpression() { return sourceExpression; }
    public void setSourceExpression(String sourceExpression) { this.sourceExpression = sourceExpression; }
}
