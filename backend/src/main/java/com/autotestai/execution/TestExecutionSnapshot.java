package com.autotestai.execution;

import com.autotestai.entity.ApiEntity;
import com.autotestai.entity.EnvironmentEntity;
import com.autotestai.entity.ProjectEntity;
import com.autotestai.entity.TestCaseEntity;

public record TestExecutionSnapshot(
        ProjectEntity project,
        ApiEntity api,
        TestCaseEntity testCase,
        EnvironmentEntity environment) {
}
