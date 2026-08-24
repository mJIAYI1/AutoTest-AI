package com.autotestai.service;

import java.util.List;

import com.autotestai.dto.execution.TestRunResponse;
import com.autotestai.exception.ApiException;
import com.autotestai.execution.TestExecutionSnapshot;
import com.autotestai.execution.TestSuiteExecutionPlan;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TestRunOrchestrator {

    private final ExecutionSnapshotService snapshotService;
    private final TestRunPersistenceService persistenceService;
    private final TestExecutionTaskService taskService;
    private final TestSuiteExecutionPlanService suiteExecutionPlanService;

    public TestRunOrchestrator(
            ExecutionSnapshotService snapshotService,
            TestRunPersistenceService persistenceService,
            TestExecutionTaskService taskService,
            TestSuiteExecutionPlanService suiteExecutionPlanService) {
        this.snapshotService = snapshotService;
        this.persistenceService = persistenceService;
        this.taskService = taskService;
        this.suiteExecutionPlanService = suiteExecutionPlanService;
    }

    public TestRunResponse startSingle(
            long userId,
            long projectId,
            long apiId,
            long testCaseId,
            Long environmentId) {
        TestExecutionSnapshot snapshot = snapshotService.load(
                userId, projectId, apiId, testCaseId, environmentId);
        if (!Boolean.TRUE.equals(snapshot.testCase().getEnabled())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TEST_CASE_DISABLED",
                    "Disabled test cases cannot be executed");
        }
        long runId = persistenceService.createSingle(userId, projectId, testCaseId, environmentId);
        try {
            taskService.executeSingle(runId, userId, projectId, apiId, testCaseId, environmentId);
        } catch (TaskRejectedException exception) {
            persistenceService.complete(
                    runId,
                    testCaseId,
                    apiId,
                    "ERROR",
                    null,
                    null,
                    List.of(),
                    List.of(),
                    "Execution queue is full; try again later");
        }
        return persistenceService.get(userId, projectId, runId);
    }

    public TestRunResponse startSuite(
            long userId,
            long projectId,
            long suiteId,
            Long environmentId) {
        TestSuiteExecutionPlan plan = suiteExecutionPlanService.load(
                userId, projectId, suiteId, environmentId);
        long runId = persistenceService.createSuite(
                userId, projectId, suiteId, environmentId, plan.steps().size());
        try {
            taskService.executeSuite(runId, plan);
        } catch (TaskRejectedException exception) {
            persistenceService.finish(
                    runId,
                    "ERROR",
                    0,
                    0,
                    1,
                    "Execution queue is full; try again later");
        }
        return persistenceService.get(userId, projectId, runId);
    }

    public TestRunResponse get(long userId, long projectId, long runId) {
        return persistenceService.get(userId, projectId, runId);
    }
}
