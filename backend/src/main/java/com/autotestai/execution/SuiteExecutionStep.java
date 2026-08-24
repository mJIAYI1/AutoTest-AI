package com.autotestai.execution;

public record SuiteExecutionStep(
        int sequenceNumber,
        TestExecutionSnapshot snapshot) {
}
