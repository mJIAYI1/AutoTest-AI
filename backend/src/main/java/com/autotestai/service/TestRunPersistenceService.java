package com.autotestai.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.autotestai.dto.execution.AssertionResult;
import com.autotestai.dto.execution.ExtractedValue;
import com.autotestai.dto.execution.TestResultResponse;
import com.autotestai.dto.execution.TestRunResponse;
import com.autotestai.entity.ExtractedVariableEntity;
import com.autotestai.entity.TestResultEntity;
import com.autotestai.entity.TestRunEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.execution.HeaderRedactor;
import com.autotestai.execution.HttpExecutionResponse;
import com.autotestai.execution.PreparedRequest;
import com.autotestai.mapper.TestRunMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestRunPersistenceService {

    private static final int MAX_ERROR_LENGTH = 4000;
    private static final TypeReference<LinkedHashMap<String, List<String>>> HEADER_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<AssertionResult>> ASSERTION_LIST = new TypeReference<>() {
    };

    private final TestRunMapper testRunMapper;
    private final ObjectMapper objectMapper;
    private final HeaderRedactor headerRedactor;

    public TestRunPersistenceService(
            TestRunMapper testRunMapper,
            ObjectMapper objectMapper,
            HeaderRedactor headerRedactor) {
        this.testRunMapper = testRunMapper;
        this.objectMapper = objectMapper;
        this.headerRedactor = headerRedactor;
    }

    @Transactional
    public long createSingle(
            long userId,
            long projectId,
            long testCaseId,
            Long environmentId) {
        TestRunEntity run = new TestRunEntity();
        run.setProjectId(projectId);
        run.setEnvironmentId(environmentId);
        run.setTriggeredByUserId(userId);
        run.setTargetTestCaseId(testCaseId);
        testRunMapper.insertSingle(run);
        return run.getId();
    }

    @Transactional
    public long createSuite(
            long userId,
            long projectId,
            long suiteId,
            Long environmentId,
            int totalCount) {
        TestRunEntity run = new TestRunEntity();
        run.setProjectId(projectId);
        run.setTestSuiteId(suiteId);
        run.setEnvironmentId(environmentId);
        run.setTriggeredByUserId(userId);
        run.setTotalCount(totalCount);
        testRunMapper.insertSuite(run);
        return run.getId();
    }

    @Transactional
    public void markRunning(long runId) {
        if (testRunMapper.markRunning(runId) == 0) {
            throw new IllegalStateException("Test run is no longer pending");
        }
    }

    @Transactional
    public void complete(
            long runId,
            long testCaseId,
            long apiId,
            String status,
            PreparedRequest request,
            HttpExecutionResponse response,
            List<AssertionResult> assertions,
            List<ExtractedValue> extractedValues,
            String errorMessage) {
        saveResult(
                runId,
                1,
                testCaseId,
                apiId,
                status,
                request,
                response,
                assertions,
                extractedValues,
                errorMessage);
        int passed = status.equals("PASS") ? 1 : 0;
        int failed = status.equals("FAIL") ? 1 : 0;
        int errors = status.equals("ERROR") ? 1 : 0;
        finish(runId, status, passed, failed, errors, errorMessage);
    }

    @Transactional
    public void saveResult(
            long runId,
            int sequenceNumber,
            long testCaseId,
            long apiId,
            String status,
            PreparedRequest request,
            HttpExecutionResponse response,
            List<AssertionResult> assertions,
            List<ExtractedValue> extractedValues,
            String errorMessage) {
        String safeError = truncate(errorMessage);
        TestResultEntity result = new TestResultEntity();
        result.setTestRunId(runId);
        result.setTestCaseId(testCaseId);
        result.setApiId(apiId);
        result.setSequenceNumber(sequenceNumber);
        result.setStatus(status);
        result.setRequestUrl(request == null ? "" : request.uri().toString());
        result.setRequestMethod(request == null ? "UNKNOWN" : request.method().name());
        result.setRequestHeadersJson(writeJson(request == null
                ? Map.of()
                : headerRedactor.redact(request.headers())));
        result.setRequestBody(request == null ? null : request.body());
        result.setResponseStatus(response == null ? null : response.statusCode());
        result.setResponseHeadersJson(writeJson(response == null
                ? Map.of()
                : headerRedactor.redact(response.headers())));
        result.setResponseBody(response == null ? null : response.body());
        result.setResponseTimeMs(response == null ? null : response.responseTimeMs());
        result.setAssertionResultsJson(writeJson(assertions));
        result.setErrorMessage(safeError);
        testRunMapper.insertResult(result);

        for (ExtractedValue value : extractedValues) {
            ExtractedVariableEntity entity = new ExtractedVariableEntity();
            entity.setTestRunId(runId);
            entity.setTestResultId(result.getId());
            entity.setName(value.name());
            entity.setValueText(value.value());
            entity.setSourceExpression(value.sourceExpression());
            testRunMapper.insertExtracted(entity);
        }
    }

    @Transactional
    public void finish(
            long runId,
            String status,
            int passedCount,
            int failedCount,
            int errorCount,
            String errorMessage) {
        if (testRunMapper.finish(
                runId,
                status,
                passedCount,
                failedCount,
                errorCount,
                truncate(errorMessage)) == 0) {
            throw new IllegalStateException("Test run could not be completed");
        }
    }

    @Transactional(readOnly = true)
    public TestRunResponse get(long userId, long projectId, long runId) {
        TestRunEntity run = testRunMapper.findOwned(runId, projectId, userId)
                .orElseThrow(TestRunPersistenceService::runNotFound);
        List<TestResultResponse> results = testRunMapper.findResultsOwned(
                runId, projectId, userId).stream()
                .map(this::toResultResponse)
                .toList();
        TestResultResponse result = "SINGLE_CASE".equals(run.getRunType()) && !results.isEmpty()
                ? results.get(0)
                : null;
        return new TestRunResponse(
                run.getId(),
                run.getProjectId(),
                run.getTargetTestCaseId(),
                run.getTestSuiteId(),
                run.getEnvironmentId(),
                run.getRunType(),
                run.getStatus(),
                valueOrZero(run.getTotalCount()),
                valueOrZero(run.getPassedCount()),
                valueOrZero(run.getFailedCount()),
                valueOrZero(run.getErrorCount()),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedAt(),
                result,
                results);
    }

    private TestResultResponse toResultResponse(TestResultEntity result) {
        List<ExtractedValue> values = testRunMapper.findExtractedByResultId(result.getId()).stream()
                .map(item -> new ExtractedValue(
                        item.getName(), item.getValueText(), item.getSourceExpression()))
                .toList();
        return new TestResultResponse(
                result.getId(),
                result.getTestCaseId(),
                result.getApiId(),
                result.getApiMethod(),
                result.getApiPath(),
                result.getApiSummary(),
                valueOrZero(result.getSequenceNumber()),
                result.getTestCaseName(),
                result.getStatus(),
                result.getRequestUrl(),
                result.getRequestMethod(),
                readHeaders(result.getRequestHeadersJson()),
                result.getRequestBody(),
                result.getResponseStatus(),
                readHeaders(result.getResponseHeadersJson()),
                result.getResponseBody(),
                result.getResponseTimeMs(),
                readAssertions(result.getAssertionResultsJson()),
                values,
                result.getErrorMessage(),
                result.getExecutedAt());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Execution result could not be encoded", exception);
        }
    }

    private Map<String, List<String>> readHeaders(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, List<String>> headers = objectMapper.readValue(value, HEADER_MAP);
            Map<String, List<String>> copy = new LinkedHashMap<>();
            headers.forEach((name, values) -> copy.put(name, List.copyOf(values)));
            return Collections.unmodifiableMap(copy);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored execution headers are invalid", exception);
        }
    }

    private List<AssertionResult> readAssertions(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(value, ASSERTION_LIST));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored assertion results are invalid", exception);
        }
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    private static ApiException runNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TEST_RUN_NOT_FOUND", "Test run was not found");
    }
}
