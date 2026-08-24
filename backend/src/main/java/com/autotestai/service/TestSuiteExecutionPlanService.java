package com.autotestai.service;

import java.util.ArrayList;
import java.util.List;

import com.autotestai.entity.ApiEntity;
import com.autotestai.entity.EnvironmentEntity;
import com.autotestai.entity.ProjectEntity;
import com.autotestai.entity.TestCaseEntity;
import com.autotestai.entity.TestSuiteCaseEntity;
import com.autotestai.entity.TestSuiteEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.execution.SuiteExecutionStep;
import com.autotestai.execution.TestExecutionSnapshot;
import com.autotestai.execution.TestSuiteExecutionPlan;
import com.autotestai.mapper.ApiMapper;
import com.autotestai.mapper.EnvironmentMapper;
import com.autotestai.mapper.ProjectMapper;
import com.autotestai.mapper.TestCaseMapper;
import com.autotestai.mapper.TestSuiteMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestSuiteExecutionPlanService {

    private final ProjectMapper projectMapper;
    private final EnvironmentMapper environmentMapper;
    private final TestSuiteMapper testSuiteMapper;
    private final ApiMapper apiMapper;
    private final TestCaseMapper testCaseMapper;

    public TestSuiteExecutionPlanService(
            ProjectMapper projectMapper,
            EnvironmentMapper environmentMapper,
            TestSuiteMapper testSuiteMapper,
            ApiMapper apiMapper,
            TestCaseMapper testCaseMapper) {
        this.projectMapper = projectMapper;
        this.environmentMapper = environmentMapper;
        this.testSuiteMapper = testSuiteMapper;
        this.apiMapper = apiMapper;
        this.testCaseMapper = testCaseMapper;
    }

    @Transactional(readOnly = true)
    public TestSuiteExecutionPlan load(
            long userId,
            long projectId,
            long suiteId,
            Long environmentId) {
        ProjectEntity project = projectMapper.findByIdAndUserId(projectId, userId)
                .orElseThrow(TestSuiteExecutionPlanService::projectNotFound);
        TestSuiteEntity suite = testSuiteMapper.findByIdOwned(suiteId, projectId, userId)
                .orElseThrow(TestSuiteExecutionPlanService::suiteNotFound);
        if (!"ACTIVE".equals(suite.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TEST_SUITE_ARCHIVED",
                    "Archived test suites cannot be executed");
        }
        EnvironmentEntity environment = environmentId == null
                ? null
                : environmentMapper.findByIdOwned(environmentId, projectId, userId)
                        .orElseThrow(TestSuiteExecutionPlanService::environmentNotFound);

        List<SuiteExecutionStep> steps = new ArrayList<>();
        for (TestSuiteCaseEntity suiteCase : testSuiteMapper.findCasesOwned(
                suiteId, projectId, userId)) {
            if (!Boolean.TRUE.equals(suiteCase.getEnabled())
                    || !Boolean.TRUE.equals(suiteCase.getTestCaseEnabled())) {
                continue;
            }
            ApiEntity api = apiMapper.findByIdOwned(
                    suiteCase.getApiId(), projectId, userId)
                    .orElseThrow(TestSuiteExecutionPlanService::apiNotFound);
            TestCaseEntity testCase = testCaseMapper.findByIdOwned(
                    suiteCase.getTestCaseId(), projectId, suiteCase.getApiId(), userId)
                    .orElseThrow(TestSuiteExecutionPlanService::testCaseNotFound);
            steps.add(new SuiteExecutionStep(
                    suiteCase.getSortOrder(),
                    new TestExecutionSnapshot(project, api, testCase, environment)));
        }
        if (steps.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TEST_SUITE_HAS_NO_ENABLED_CASES",
                    "The test suite has no enabled test cases to execute");
        }
        return new TestSuiteExecutionPlan(
                suite.getId(),
                suite.getName(),
                Boolean.TRUE.equals(suite.getStopOnFailure()),
                steps);
    }

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project was not found");
    }

    private static ApiException suiteNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TEST_SUITE_NOT_FOUND", "Test suite was not found");
    }

    private static ApiException environmentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "Environment was not found");
    }

    private static ApiException apiNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "API_NOT_FOUND", "API definition was not found");
    }

    private static ApiException testCaseNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TEST_CASE_NOT_FOUND", "Test case was not found");
    }
}
