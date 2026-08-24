package com.autotestai.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.autotestai.assertion.AssertionEngine;
import com.autotestai.dto.execution.AssertionResult;
import com.autotestai.dto.execution.ExtractedValue;
import com.autotestai.dto.testcase.ExtractionRule;
import com.autotestai.dto.testcase.TestAssertion;
import com.autotestai.execution.ExecutionConfigReader;
import com.autotestai.execution.HttpExecutionClient;
import com.autotestai.execution.HttpExecutionResponse;
import com.autotestai.execution.PreparedRequest;
import com.autotestai.execution.RequestPreparer;
import com.autotestai.execution.ResponseVariableExtractor;
import com.autotestai.execution.SuiteExecutionStep;
import com.autotestai.execution.TestExecutionSnapshot;
import com.autotestai.execution.TestSuiteExecutionPlan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TestExecutionTaskService {

    private static final Logger log = LoggerFactory.getLogger(TestExecutionTaskService.class);

    private final ExecutionSnapshotService snapshotService;
    private final RequestPreparer requestPreparer;
    private final HttpExecutionClient httpExecutionClient;
    private final ExecutionConfigReader configReader;
    private final AssertionEngine assertionEngine;
    private final ResponseVariableExtractor variableExtractor;
    private final TestRunPersistenceService persistenceService;

    public TestExecutionTaskService(
            ExecutionSnapshotService snapshotService,
            RequestPreparer requestPreparer,
            HttpExecutionClient httpExecutionClient,
            ExecutionConfigReader configReader,
            AssertionEngine assertionEngine,
            ResponseVariableExtractor variableExtractor,
            TestRunPersistenceService persistenceService) {
        this.snapshotService = snapshotService;
        this.requestPreparer = requestPreparer;
        this.httpExecutionClient = httpExecutionClient;
        this.configReader = configReader;
        this.assertionEngine = assertionEngine;
        this.variableExtractor = variableExtractor;
        this.persistenceService = persistenceService;
    }

    @Async("testExecutionTaskExecutor")
    public void executeSingle(
            long runId,
            long userId,
            long projectId,
            long apiId,
            long testCaseId,
            Long environmentId) {
        try {
            persistenceService.markRunning(runId);
            TestExecutionSnapshot snapshot = snapshotService.load(
                    userId, projectId, apiId, testCaseId, environmentId);
            CaseExecutionOutcome outcome = executeCase(snapshot, Map.of());
            persistenceService.saveResult(
                    runId,
                    1,
                    testCaseId,
                    apiId,
                    outcome.status(),
                    outcome.request(),
                    outcome.response(),
                    outcome.assertions(),
                    outcome.extractedValues(),
                    outcome.errorMessage());
            persistenceService.finish(
                    runId,
                    outcome.status(),
                    outcome.is("PASS") ? 1 : 0,
                    outcome.is("FAIL") ? 1 : 0,
                    outcome.is("ERROR") ? 1 : 0,
                    outcome.errorMessage());
        } catch (Exception exception) {
            failRun(runId, 0, 0, 1, exception);
        }
    }

    @Async("testExecutionTaskExecutor")
    public void executeSuite(long runId, TestSuiteExecutionPlan plan) {
        int passed = 0;
        int failed = 0;
        int errors = 0;
        String stopMessage = null;
        try {
            persistenceService.markRunning(runId);
            Map<String, String> runtimeVariables = new LinkedHashMap<>();
            for (SuiteExecutionStep step : plan.steps()) {
                TestExecutionSnapshot snapshot = step.snapshot();
                CaseExecutionOutcome outcome = executeCase(snapshot, runtimeVariables);
                persistenceService.saveResult(
                        runId,
                        step.sequenceNumber(),
                        snapshot.testCase().getId(),
                        snapshot.api().getId(),
                        outcome.status(),
                        outcome.request(),
                        outcome.response(),
                        outcome.assertions(),
                        outcome.extractedValues(),
                        outcome.errorMessage());

                if (outcome.is("PASS")) passed++;
                if (outcome.is("FAIL")) failed++;
                if (outcome.is("ERROR")) errors++;
                for (ExtractedValue value : outcome.extractedValues()) {
                    runtimeVariables.put(value.name(), value.value());
                }

                if (plan.stopOnFailure() && !outcome.is("PASS")) {
                    stopMessage = "STOP_ON_FAILURE stopped the suite after step "
                            + step.sequenceNumber() + " (" + snapshot.testCase().getName() + ")";
                    break;
                }
            }
            String status = errors > 0 ? "ERROR" : failed > 0 ? "FAIL" : "PASS";
            String message = stopMessage != null
                    ? stopMessage
                    : errors > 0 ? errors + " test case(s) ended with execution errors" : null;
            persistenceService.finish(runId, status, passed, failed, errors, message);
        } catch (Exception exception) {
            failRun(runId, passed, failed, Math.max(1, errors), exception);
        }
    }

    private CaseExecutionOutcome executeCase(
            TestExecutionSnapshot snapshot,
            Map<String, String> runtimeVariables) {
        PreparedRequest request = null;
        HttpExecutionResponse response = null;
        List<AssertionResult> assertionResults = List.of();
        List<ExtractedValue> extractedValues = List.of();
        try {
            request = requestPreparer.prepare(snapshot, runtimeVariables);
            response = httpExecutionClient.execute(request);
            List<TestAssertion> assertions = configReader.assertions(
                    snapshot.testCase().getAssertionsJson());
            assertionResults = assertionEngine.evaluate(assertions, response);
            List<ExtractionRule> extractions = configReader.extractions(
                    snapshot.testCase().getExtractionRulesJson());
            extractedValues = variableExtractor.extract(extractions, response.body());
            String status = assertionResults.stream().allMatch(AssertionResult::passed)
                    ? "PASS"
                    : "FAIL";
            return new CaseExecutionOutcome(
                    status, request, response, assertionResults, extractedValues, null);
        } catch (Exception exception) {
            String message = safeMessage(exception);
            log.warn(
                    "Test case {} ended with an execution error: {}",
                    snapshot.testCase().getId(),
                    message);
            return new CaseExecutionOutcome(
                    "ERROR", request, response, assertionResults, extractedValues, message);
        }
    }

    private void failRun(
            long runId,
            int passed,
            int failed,
            int errors,
            Exception exception) {
        String message = safeMessage(exception);
        log.error("Test run {} could not complete normally: {}", runId, message, exception);
        try {
            persistenceService.finish(runId, "ERROR", passed, failed, errors, message);
        } catch (Exception persistenceFailure) {
            log.error("Test run {} could not persist its terminal state", runId, persistenceFailure);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private record CaseExecutionOutcome(
            String status,
            PreparedRequest request,
            HttpExecutionResponse response,
            List<AssertionResult> assertions,
            List<ExtractedValue> extractedValues,
            String errorMessage) {

        private boolean is(String expectedStatus) {
            return expectedStatus.equals(status);
        }
    }
}
