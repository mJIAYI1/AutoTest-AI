package com.autotestai.service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.autotestai.dto.testcase.AssertionType;
import com.autotestai.dto.testcase.CreateTestCaseRequest;
import com.autotestai.dto.testcase.ExtractionRule;
import com.autotestai.dto.testcase.TestAssertion;
import com.autotestai.dto.testcase.TestCaseResponse;
import com.autotestai.dto.testcase.TestCaseType;
import com.autotestai.dto.testcase.UpdateTestCaseRequest;
import com.autotestai.entity.TestCaseEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.ApiMapper;
import com.autotestai.mapper.ProjectMapper;
import com.autotestai.mapper.TestCaseMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestCaseService {

    private static final Pattern HEADER_NAME =
            Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final int MAX_HEADER_VALUE_LENGTH = 8192;
    private static final int MAX_PARAMETER_VALUE_LENGTH = 10_000;
    private static final int MAX_REQUEST_BODY_BYTES = 1_048_576;
    private static final Set<String> JSON_VALUE_TYPES =
            Set.of("STRING", "NUMBER", "INTEGER", "BOOLEAN", "ARRAY", "OBJECT", "NULL");
    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP =
            new TypeReference<>() {
            };
    private static final TypeReference<List<TestAssertion>> ASSERTION_LIST =
            new TypeReference<>() {
            };
    private static final TypeReference<List<ExtractionRule>> EXTRACTION_LIST =
            new TypeReference<>() {
            };

    private final ProjectMapper projectMapper;
    private final ApiMapper apiMapper;
    private final TestCaseMapper testCaseMapper;
    private final ObjectMapper objectMapper;

    public TestCaseService(
            ProjectMapper projectMapper,
            ApiMapper apiMapper,
            TestCaseMapper testCaseMapper,
            ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.apiMapper = apiMapper;
        this.testCaseMapper = testCaseMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TestCaseResponse create(
            long userId,
            long projectId,
            long apiId,
            CreateTestCaseRequest request) {
        requireOwnedApi(userId, projectId, apiId);
        String name = request.name().trim();
        ensureNameAvailable(projectId, apiId, userId, name, null);

        TestCaseEntity testCase = toEntity(
                apiId,
                name,
                request.description(),
                request.type(),
                request.requestHeaders(),
                request.pathParameters(),
                request.queryParameters(),
                request.requestBody(),
                request.assertions(),
                request.extractionRules(),
                request.enabled());
        try {
            testCaseMapper.insert(testCase);
        } catch (DuplicateKeyException exception) {
            throw testCaseNameTaken();
        }
        return toResponse(requireOwnedTestCase(testCase.getId(), projectId, apiId, userId));
    }

    public void validateDraft(CreateTestCaseRequest request) {
        toEntity(
                0L,
                request.name().trim(),
                request.description(),
                request.type(),
                request.requestHeaders(),
                request.pathParameters(),
                request.queryParameters(),
                request.requestBody(),
                request.assertions(),
                request.extractionRules(),
                request.enabled());
    }

    @Transactional(readOnly = true)
    public List<TestCaseResponse> list(long userId, long projectId, long apiId) {
        requireOwnedApi(userId, projectId, apiId);
        return testCaseMapper.findAllOwned(projectId, apiId, userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TestCaseResponse get(long userId, long projectId, long apiId, long testCaseId) {
        requireOwnedApi(userId, projectId, apiId);
        return toResponse(requireOwnedTestCase(testCaseId, projectId, apiId, userId));
    }

    @Transactional
    public TestCaseResponse update(
            long userId,
            long projectId,
            long apiId,
            long testCaseId,
            UpdateTestCaseRequest request) {
        requireOwnedApi(userId, projectId, apiId);
        requireOwnedTestCase(testCaseId, projectId, apiId, userId);
        String name = request.name().trim();
        ensureNameAvailable(projectId, apiId, userId, name, testCaseId);

        TestCaseEntity testCase = toEntity(
                apiId,
                name,
                request.description(),
                request.type(),
                request.requestHeaders(),
                request.pathParameters(),
                request.queryParameters(),
                request.requestBody(),
                request.assertions(),
                request.extractionRules(),
                request.enabled());
        try {
            int updated = testCaseMapper.updateOwned(
                    testCaseId,
                    projectId,
                    apiId,
                    userId,
                    request.version(),
                    testCase);
            if (updated == 0) {
                throw staleVersion();
            }
        } catch (DuplicateKeyException exception) {
            throw testCaseNameTaken();
        }
        return toResponse(requireOwnedTestCase(testCaseId, projectId, apiId, userId));
    }

    @Transactional
    public void delete(long userId, long projectId, long apiId, long testCaseId) {
        requireOwnedApi(userId, projectId, apiId);
        if (testCaseMapper.deleteOwned(testCaseId, projectId, apiId, userId) == 0) {
            throw testCaseNotFound();
        }
    }

    private TestCaseEntity toEntity(
            long apiId,
            String name,
            String description,
            TestCaseType type,
            Map<String, String> requestHeaders,
            Map<String, String> pathParameters,
            Map<String, String> queryParameters,
            JsonNode requestBody,
            List<TestAssertion> assertions,
            List<ExtractionRule> extractionRules,
            Boolean enabled) {
        Map<String, String> headers = validateHeaders(requestHeaders);
        Map<String, String> pathValues = validateParameters(pathParameters, "path parameters");
        Map<String, String> queryValues = validateParameters(queryParameters, "query parameters");
        List<TestAssertion> validatedAssertions = validateAssertions(assertions);
        List<ExtractionRule> validatedExtractions = validateExtractions(extractionRules);

        TestCaseEntity testCase = new TestCaseEntity();
        testCase.setApiId(apiId);
        testCase.setName(name);
        testCase.setDescription(normalizeNullable(description));
        testCase.setType(type.name());
        testCase.setRequestHeadersJson(writeJson(headers));
        testCase.setPathParametersJson(writeJson(pathValues));
        testCase.setQueryParametersJson(writeJson(queryValues));
        testCase.setRequestBodyJson(writeRequestBody(requestBody));
        testCase.setAssertionsJson(writeJson(validatedAssertions));
        testCase.setExtractionRulesJson(writeJson(validatedExtractions));
        testCase.setEnabled(enabled);
        return testCase;
    }

    private void requireOwnedApi(long userId, long projectId, long apiId) {
        if (projectMapper.findByIdAndUserId(projectId, userId).isEmpty()) {
            throw projectNotFound();
        }
        if (apiMapper.findByIdOwned(apiId, projectId, userId).isEmpty()) {
            throw apiNotFound();
        }
    }

    private TestCaseEntity requireOwnedTestCase(
            long testCaseId,
            long projectId,
            long apiId,
            long userId) {
        return testCaseMapper.findByIdOwned(testCaseId, projectId, apiId, userId)
                .orElseThrow(TestCaseService::testCaseNotFound);
    }

    private void ensureNameAvailable(
            long projectId,
            long apiId,
            long userId,
            String name,
            Long currentTestCaseId) {
        testCaseMapper.findByNameOwned(projectId, apiId, userId, name)
                .filter(testCase -> currentTestCaseId == null
                        || !testCase.getId().equals(currentTestCaseId))
                .ifPresent(testCase -> {
                    throw testCaseNameTaken();
                });
    }

    private TestCaseResponse toResponse(TestCaseEntity testCase) {
        return new TestCaseResponse(
                testCase.getId(),
                testCase.getApiId(),
                testCase.getName(),
                testCase.getDescription(),
                TestCaseType.valueOf(testCase.getType()),
                readMap(testCase.getRequestHeadersJson()),
                readMap(testCase.getPathParametersJson()),
                readMap(testCase.getQueryParametersJson()),
                readBody(testCase.getRequestBodyJson()),
                readList(testCase.getAssertionsJson(), ASSERTION_LIST),
                readList(testCase.getExtractionRulesJson(), EXTRACTION_LIST),
                Boolean.TRUE.equals(testCase.getEnabled()),
                testCase.getVersion(),
                testCase.getCreatedAt(),
                testCase.getUpdatedAt());
    }

    private Map<String, String> validateHeaders(Map<String, String> values) {
        Map<String, String> normalized = copyOrEmpty(values);
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null
                    || name.length() > 100
                    || !HEADER_NAME.matcher(name).matches()
                    || value == null
                    || value.length() > MAX_HEADER_VALUE_LENGTH
                    || value.indexOf('\r') >= 0
                    || value.indexOf('\n') >= 0) {
                throw invalidConfig("Request headers contain an invalid name or value");
            }
        }
        return normalized;
    }

    private Map<String, String> validateParameters(Map<String, String> values, String label) {
        Map<String, String> normalized = copyOrEmpty(values);
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null
                    || name.isBlank()
                    || name.length() > 200
                    || name.chars().anyMatch(Character::isISOControl)
                    || value == null
                    || value.length() > MAX_PARAMETER_VALUE_LENGTH) {
                throw invalidConfig("Request " + label + " contain an invalid name or value");
            }
        }
        return normalized;
    }

    private List<TestAssertion> validateAssertions(List<TestAssertion> assertions) {
        if (assertions == null || assertions.isEmpty()) {
            throw invalidConfig("At least one assertion is required");
        }
        return assertions.stream().map(this::validateAssertion).toList();
    }

    private TestAssertion validateAssertion(TestAssertion assertion) {
        if (assertion == null || assertion.type() == null) {
            throw invalidConfig("Assertion type is required");
        }
        String expression = normalizeNullable(assertion.expression());
        JsonNode expected = assertion.expected();
        switch (assertion.type()) {
            case STATUS_CODE -> {
                requireNoExpression(expression, assertion.type());
                if (expected == null || !expected.isIntegralNumber()
                        || expected.longValue() < 100 || expected.longValue() > 599) {
                    throw invalidAssertion("Status code must be an integer from 100 to 599");
                }
            }
            case JSON_PATH_EXISTS -> {
                requireJsonPath(expression);
                if (expected != null && !expected.isNull()) {
                    throw invalidAssertion("JSON path exists assertion does not accept an expected value");
                }
            }
            case JSON_PATH_EQUALS -> {
                requireJsonPath(expression);
                if (expected == null) {
                    throw invalidAssertion("JSON path equals assertion requires an expected value");
                }
            }
            case JSON_PATH_TYPE -> {
                requireJsonPath(expression);
                if (expected == null || !expected.isTextual()
                        || !JSON_VALUE_TYPES.contains(expected.textValue().toUpperCase(Locale.ROOT))) {
                    throw invalidAssertion("JSON path type must be STRING, NUMBER, INTEGER, BOOLEAN, ARRAY, OBJECT or NULL");
                }
            }
            case RESPONSE_TIME_LT -> {
                requireNoExpression(expression, assertion.type());
                if (expected == null || !expected.isIntegralNumber()
                        || expected.longValue() <= 0 || expected.longValue() > 300_000) {
                    throw invalidAssertion("Response time threshold must be between 1 and 300000 milliseconds");
                }
            }
            case BODY_CONTAINS -> {
                requireNoExpression(expression, assertion.type());
                if (expected == null || !expected.isTextual()
                        || expected.textValue().isBlank() || expected.textValue().length() > 10_000) {
                    throw invalidAssertion("Body contains assertion requires a non-empty string");
                }
            }
            default -> throw invalidAssertion("Unsupported assertion type");
        }
        return new TestAssertion(assertion.type(), expression, expected);
    }

    private List<ExtractionRule> validateExtractions(List<ExtractionRule> extractionRules) {
        if (extractionRules == null || extractionRules.isEmpty()) {
            return List.of();
        }
        Set<String> names = new HashSet<>();
        return extractionRules.stream().map(rule -> {
            if (rule == null || rule.name() == null || rule.jsonPath() == null) {
                throw invalidConfig("Extraction rule name and JSON path are required");
            }
            String name = rule.name().trim();
            String jsonPath = rule.jsonPath().trim();
            if (!names.add(name)) {
                throw invalidConfig("Extraction rule names must be unique within a test case");
            }
            requireJsonPath(jsonPath);
            return new ExtractionRule(name, jsonPath);
        }).toList();
    }

    private static void requireJsonPath(String expression) {
        if (expression == null || !expression.startsWith("$")) {
            throw invalidAssertion("JSON path expressions must start with $");
        }
    }

    private static void requireNoExpression(String expression, AssertionType type) {
        if (expression != null) {
            throw invalidAssertion(type + " assertion does not accept an expression");
        }
    }

    private String writeRequestBody(JsonNode requestBody) {
        if (requestBody == null || requestBody.isNull()) {
            return null;
        }
        String json = writeJson(requestBody);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BODY_BYTES) {
            throw invalidConfig("Request body exceeds the 1 MB limit");
        }
        return json;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalidConfig("Test case configuration could not be encoded as JSON");
        }
    }

    private Map<String, String> readMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return Collections.unmodifiableMap(objectMapper.readValue(value, STRING_MAP));
        } catch (JsonProcessingException exception) {
            throw storedJsonInvalid(exception);
        }
    }

    private JsonNode readBody(String value) {
        if (value == null || value.isBlank() || value.equals("null")) {
            return NullNode.getInstance();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node == null ? NullNode.getInstance() : node;
        } catch (JsonProcessingException exception) {
            throw storedJsonInvalid(exception);
        }
    }

    private <T> List<T> readList(String value, TypeReference<List<T>> type) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(value, type));
        } catch (JsonProcessingException exception) {
            throw storedJsonInvalid(exception);
        }
    }

    private static Map<String, String> copyOrEmpty(Map<String, String> value) {
        return value == null || value.isEmpty() ? Map.of() : new LinkedHashMap<>(value);
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static IllegalStateException storedJsonInvalid(JsonProcessingException exception) {
        return new IllegalStateException("Stored test case JSON is invalid", exception);
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

    private static ApiException testCaseNameTaken() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "TEST_CASE_NAME_TAKEN",
                "A test case with this name already exists for the API");
    }

    private static ApiException staleVersion() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "STALE_TEST_CASE_VERSION",
                "The test case was changed by another request; reload it before saving");
    }

    private static ApiException invalidConfig(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TEST_CASE", message);
    }

    private static ApiException invalidAssertion(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ASSERTION", message);
    }
}
