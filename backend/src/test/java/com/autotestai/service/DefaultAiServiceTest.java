package com.autotestai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autotestai.ai.AiModelGateway;
import com.autotestai.ai.AiPromptSanitizer;
import com.autotestai.dto.ai.AiDiagnosisSeverity;
import com.autotestai.dto.ai.AiFailureDiagnosis;
import com.autotestai.dto.ai.AiFailureDiagnosisResponse;
import com.autotestai.dto.ai.AiGeneratedTestCase;
import com.autotestai.dto.ai.AiGeneratedTestCases;
import com.autotestai.dto.ai.AiTestCaseGenerationRequest;
import com.autotestai.dto.ai.AiTestCaseGenerationResponse;
import com.autotestai.dto.testcase.AssertionType;
import com.autotestai.dto.testcase.TestAssertion;
import com.autotestai.dto.testcase.TestCaseType;
import com.autotestai.entity.ApiEntity;
import com.autotestai.entity.AiFailureDiagnosisEntity;
import com.autotestai.entity.ProjectEntity;
import com.autotestai.entity.TestCaseEntity;
import com.autotestai.entity.TestResultEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.ApiMapper;
import com.autotestai.mapper.AiFailureDiagnosisMapper;
import com.autotestai.mapper.ProjectMapper;
import com.autotestai.mapper.TestCaseMapper;
import com.autotestai.mapper.TestRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DefaultAiServiceTest {

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private ApiMapper apiMapper;

    @Mock
    private TestCaseMapper testCaseMapper;

    @Mock
    private TestRunMapper testRunMapper;

    @Mock
    private AiFailureDiagnosisMapper failureDiagnosisMapper;

    @Mock
    private TestCaseService testCaseService;

    @Mock
    private AiModelGateway aiModelGateway;

    private ValidatorFactory validatorFactory;
    private DefaultAiService aiService;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        ObjectMapper objectMapper = new ObjectMapper();
        aiService = new DefaultAiService(
                projectMapper,
                apiMapper,
                testCaseMapper,
                testRunMapper,
                failureDiagnosisMapper,
                testCaseService,
                aiModelGateway,
                new AiPromptSanitizer(objectMapper),
                objectMapper,
                validatorFactory.getValidator());
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void reportsAServiceUnavailableErrorWhenCredentialsAreMissing() {
        when(aiModelGateway.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> aiService.generateTestCases(
                7L,
                20L,
                30L,
                new AiTestCaseGenerationRequest(4, null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getCode()).isEqualTo("AI_NOT_CONFIGURED");
                });

        verifyNoInteractions(projectMapper, apiMapper, testCaseMapper, testCaseService);
    }

    @Test
    void createsValidatedCandidatesAndRenamesAConflictBeforeImport() {
        allowOwnedApi();
        when(aiModelGateway.provider()).thenReturn("SiliconFlow");
        when(aiModelGateway.model()).thenReturn("Qwen/test-model");
        TestCaseEntity existing = new TestCaseEntity();
        existing.setName("Normal list");
        existing.setType("NORMAL");
        existing.setAssertionsJson("[]");
        when(testCaseMapper.findAllOwned(20L, 30L, 7L)).thenReturn(List.of(existing));
        when(aiModelGateway.generateTestCases(anyString(), anyString()))
                .thenReturn(new AiGeneratedTestCases(List.of(validCandidate("Normal list"))));

        AiTestCaseGenerationResponse response = aiService.generateTestCases(
                7L,
                20L,
                30L,
                new AiTestCaseGenerationRequest(1, "cover paging boundaries"));

        assertThat(response.provider()).isEqualTo("SiliconFlow");
        assertThat(response.model()).isEqualTo("Qwen/test-model");
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().get(0).name()).isEqualTo("Normal list (AI 2)");
        assertThat(response.warnings()).singleElement().asString().contains("AI 2");
        verify(testCaseService).validateDraft(any());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(aiModelGateway).generateTestCases(anyString(), prompt.capture());
        assertThat(prompt.getValue())
                .contains("\"method\":\"GET\"")
                .contains("cover paging boundaries")
                .contains("Normal list");
    }

    @Test
    void rejectsAProviderCandidateThatFailsTheJavaExecutionRules() {
        allowOwnedApi();
        when(testCaseMapper.findAllOwned(20L, 30L, 7L)).thenReturn(List.of());
        when(aiModelGateway.generateTestCases(anyString(), anyString()))
                .thenReturn(new AiGeneratedTestCases(List.of(validCandidate("Boundary list"))));
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ASSERTION", "invalid"))
                .when(testCaseService).validateDraft(any());

        assertThatThrownBy(() -> aiService.generateTestCases(
                7L,
                20L,
                30L,
                new AiTestCaseGenerationRequest(1, null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getCode()).isEqualTo("AI_RESPONSE_INVALID");
                });
    }

    @Test
    void analyzesAStoredFailureRedactsSecretsAndPersistsStructuredOutput() {
        allowFailureResult("FAIL");
        when(aiModelGateway.provider()).thenReturn("SiliconFlow");
        when(aiModelGateway.model()).thenReturn("Qwen/test-model");
        when(aiModelGateway.analyzeFailure(anyString(), anyString())).thenReturn(new AiFailureDiagnosis(
                "The endpoint returned an unexpected server error",
                AiDiagnosisSeverity.HIGH,
                List.of("The missing user path reaches an unhandled branch"),
                List.of("UserController#getById", "global exception mapping"),
                List.of("Return the documented 404 response when the user does not exist")));
        when(failureDiagnosisMapper.findOwned(60L, 50L, 20L, 7L))
                .thenReturn(Optional.of(savedDiagnosis()));

        AiFailureDiagnosisResponse response = aiService.analyzeFailure(7L, 20L, 50L, 60L);

        assertThat(response.diagnosis().severity()).isEqualTo(AiDiagnosisSeverity.HIGH);
        assertThat(response.diagnosis().possibleCauses()).hasSize(1);
        verify(failureDiagnosisMapper).upsert(any());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(aiModelGateway).analyzeFailure(anyString(), prompt.capture());
        assertThat(prompt.getValue())
                .contains("/users/{id}")
                .contains("STATUS_CODE")
                .contains("[REDACTED]")
                .doesNotContain("supersecret")
                .doesNotContain("testcase-secret")
                .doesNotContain("url-secret")
                .doesNotContain("query-secret");
    }

    @Test
    void refusesToAnalyzeAPassingResultWithoutCallingTheModel() {
        when(aiModelGateway.isConfigured()).thenReturn(true);
        TestResultEntity result = new TestResultEntity();
        result.setId(60L);
        result.setStatus("PASS");
        when(testRunMapper.findResultOwned(60L, 50L, 20L, 7L)).thenReturn(Optional.of(result));

        assertThatThrownBy(() -> aiService.analyzeFailure(7L, 20L, 50L, 60L))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("AI_DIAGNOSIS_NOT_APPLICABLE");
                });

        verify(aiModelGateway, never()).analyzeFailure(anyString(), anyString());
        verifyNoInteractions(apiMapper, testCaseMapper, failureDiagnosisMapper);
    }

    @Test
    void rejectsAnInvalidStructuredFailureDiagnosis() {
        allowFailureResult("ERROR");
        when(aiModelGateway.analyzeFailure(anyString(), anyString())).thenReturn(new AiFailureDiagnosis(
                "",
                AiDiagnosisSeverity.MEDIUM,
                List.of(),
                List.of(),
                List.of()));

        assertThatThrownBy(() -> aiService.analyzeFailure(7L, 20L, 50L, 60L))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getCode()).isEqualTo("AI_RESPONSE_INVALID");
                });

        verify(failureDiagnosisMapper, never()).upsert(any());
    }

    private void allowOwnedApi() {
        ProjectEntity project = new ProjectEntity();
        project.setId(20L);
        ApiEntity api = new ApiEntity();
        api.setId(30L);
        api.setProjectId(20L);
        api.setMethod("GET");
        api.setPath("/users");
        api.setSummary("List users");
        api.setParametersJson("[]");
        api.setResponseSchemaJson("{\"type\":\"object\"}");

        when(aiModelGateway.isConfigured()).thenReturn(true);
        when(projectMapper.findByIdAndUserId(20L, 7L)).thenReturn(Optional.of(project));
        when(apiMapper.findByIdOwned(30L, 20L, 7L)).thenReturn(Optional.of(api));
    }

    private void allowFailureResult(String status) {
        when(aiModelGateway.isConfigured()).thenReturn(true);
        TestResultEntity result = new TestResultEntity();
        result.setId(60L);
        result.setTestRunId(50L);
        result.setTestCaseId(40L);
        result.setApiId(30L);
        result.setStatus(status);
        result.setRequestUrl("https://api.example.test/users/999999?token=url-secret");
        result.setRequestMethod("GET");
        result.setRequestHeadersJson("{\"Authorization\":[\"***\"]}");
        result.setRequestBody("{\"password\":\"supersecret\",\"name\":\"Alice\"}");
        result.setResponseStatus(500);
        result.setResponseHeadersJson("{\"Content-Type\":[\"application/json\"]}");
        result.setResponseBody("{\"message\":\"Internal Server Error\"}");
        result.setResponseTimeMs(120L);
        result.setAssertionResultsJson("[{\"type\":\"STATUS_CODE\",\"expected\":404,\"actual\":500,\"passed\":false}]");
        when(testRunMapper.findResultOwned(60L, 50L, 20L, 7L)).thenReturn(Optional.of(result));

        ApiEntity api = new ApiEntity();
        api.setId(30L);
        api.setMethod("GET");
        api.setPath("/users/{id}");
        api.setSummary("Get user");
        api.setParametersJson("[]");
        api.setResponseSchemaJson("{\"type\":\"object\"}");
        when(apiMapper.findByIdOwned(30L, 20L, 7L)).thenReturn(Optional.of(api));

        TestCaseEntity testCase = new TestCaseEntity();
        testCase.setId(40L);
        testCase.setName("Missing user");
        testCase.setDescription("Unknown users should return 404");
        testCase.setType("NEGATIVE");
        testCase.setRequestHeadersJson("{\"Authorization\":\"Bearer testcase-secret\"}");
        testCase.setPathParametersJson("{\"id\":\"999999\"}");
        testCase.setQueryParametersJson("{\"apiKey\":\"query-secret\"}");
        testCase.setRequestBodyJson("null");
        testCase.setAssertionsJson("[{\"type\":\"STATUS_CODE\",\"expected\":404}]");
        when(testCaseMapper.findByIdOwned(40L, 20L, 30L, 7L)).thenReturn(Optional.of(testCase));
    }

    private static AiFailureDiagnosisEntity savedDiagnosis() {
        AiFailureDiagnosisEntity entity = new AiFailureDiagnosisEntity();
        entity.setId(70L);
        entity.setTestResultId(60L);
        entity.setProvider("SiliconFlow");
        entity.setModel("Qwen/test-model");
        entity.setSummary("The endpoint returned an unexpected server error");
        entity.setSeverity("HIGH");
        entity.setPossibleCausesJson("[\"The missing user path reaches an unhandled branch\"]");
        entity.setCheckLocationsJson("[\"UserController#getById\"]");
        entity.setRepairSuggestionsJson("[\"Return the documented 404 response\"]");
        entity.setCreatedAt(java.time.LocalDateTime.now());
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        return entity;
    }

    private static AiGeneratedTestCase validCandidate(String name) {
        return new AiGeneratedTestCase(
                name,
                "Returns the user list",
                TestCaseType.NORMAL,
                Map.of("Accept", "application/json"),
                Map.of(),
                Map.of("page", "1"),
                null,
                List.of(new TestAssertion(
                        AssertionType.STATUS_CODE,
                        null,
                        IntNode.valueOf(200))),
                List.of());
    }
}
