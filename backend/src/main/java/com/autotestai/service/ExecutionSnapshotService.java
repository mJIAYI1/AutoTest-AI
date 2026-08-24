package com.autotestai.service;

import com.autotestai.entity.ApiEntity;
import com.autotestai.entity.EnvironmentEntity;
import com.autotestai.entity.ProjectEntity;
import com.autotestai.entity.TestCaseEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.execution.TestExecutionSnapshot;
import com.autotestai.mapper.ApiMapper;
import com.autotestai.mapper.EnvironmentMapper;
import com.autotestai.mapper.ProjectMapper;
import com.autotestai.mapper.TestCaseMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionSnapshotService {

    private final ProjectMapper projectMapper;
    private final ApiMapper apiMapper;
    private final TestCaseMapper testCaseMapper;
    private final EnvironmentMapper environmentMapper;

    public ExecutionSnapshotService(
            ProjectMapper projectMapper,
            ApiMapper apiMapper,
            TestCaseMapper testCaseMapper,
            EnvironmentMapper environmentMapper) {
        this.projectMapper = projectMapper;
        this.apiMapper = apiMapper;
        this.testCaseMapper = testCaseMapper;
        this.environmentMapper = environmentMapper;
    }

    @Transactional(readOnly = true)
    public TestExecutionSnapshot load(
            long userId,
            long projectId,
            long apiId,
            long testCaseId,
            Long environmentId) {
        ProjectEntity project = projectMapper.findByIdAndUserId(projectId, userId)
                .orElseThrow(ExecutionSnapshotService::projectNotFound);
        ApiEntity api = apiMapper.findByIdOwned(apiId, projectId, userId)
                .orElseThrow(ExecutionSnapshotService::apiNotFound);
        TestCaseEntity testCase = testCaseMapper.findByIdOwned(testCaseId, projectId, apiId, userId)
                .orElseThrow(ExecutionSnapshotService::testCaseNotFound);
        EnvironmentEntity environment = environmentId == null
                ? null
                : environmentMapper.findByIdOwned(environmentId, projectId, userId)
                        .orElseThrow(ExecutionSnapshotService::environmentNotFound);
        return new TestExecutionSnapshot(project, api, testCase, environment);
    }

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project was not found");
    }

    private static ApiException apiNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "API_NOT_FOUND", "API definition was not found");
    }

    private static ApiException testCaseNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TEST_CASE_NOT_FOUND", "Test case was not found");
    }

    private static ApiException environmentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "Environment was not found");
    }
}
