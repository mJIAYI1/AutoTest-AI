package com.autotestai.execution;

import java.util.List;

public record TestSuiteExecutionPlan(
        long suiteId,
        String suiteName,
        boolean stopOnFailure,
        List<SuiteExecutionStep> steps) {

    public TestSuiteExecutionPlan {
        steps = List.copyOf(steps);
    }
}
