package com.autotestai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autotestai.dto.testcase.AssertionType;
import com.autotestai.dto.testcase.CreateTestCaseRequest;
import com.autotestai.dto.testcase.ExtractionRule;
import com.autotestai.dto.testcase.TestAssertion;
import com.autotestai.dto.testcase.TestCaseResponse;
import com.autotestai.dto.testcase.TestCaseType;
import com.autotestai.dto.testcase.UpdateTestCaseRequest;
import com.autotestai.entity.ApiEntity;
import com.autotestai.entity.ProjectEntity;
import com.autotestai.entity.TestCaseEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.ApiMapper;
import com.autotestai.mapper.ProjectMapper;
import com.autotestai.mapper.TestCaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TestCaseServiceTest {

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private ApiMapper apiMapper;

    @Mock
    private TestCaseMapper testCaseMapper;

    private ObjectMapper objectMapper;
    private TestCaseService testCaseService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testCaseService = new TestCaseService(projectMapper, apiMapper, testCaseMapper, objectMapper);
    }

    @Test
    void createValidatesAndSerializesExecutableConfiguration() {
        allowOwnedApi();
        when(testCaseMapper.findByNameOwned(20L, 30L, 7L, "Successful login"))
                .thenReturn(Optional.empty());
        when(testCaseMapper.insert(any(TestCaseEntity.class))).thenAnswer(invocation -> {
            TestCaseEntity entity = invocation.getArgument(0);
            entity.setId(40L);
            return 1;
        });
        when(testCaseMapper.findByIdOwned(40L, 20L, 30L, 7L))
                .thenReturn(Optional.of(storedTestCase()));

        TestCaseResponse response = testCaseService.create(
                7L,
                20L,
                30L,
                new CreateTestCaseRequest(
                        " Successful login ",
                        "Returns a token",
                        TestCaseType.NORMAL,
                        Map.of("Content-Type", "application/json"),
                        Map.of(),
                        Map.of("trace", "{{trace_id}}"),
                        objectMapper.createObjectNode().put("username", "admin"),
                        List.of(
                                new TestAssertion(AssertionType.STATUS_CODE, null, IntNode.valueOf(200)),
                                new TestAssertion(AssertionType.JSON_PATH_EXISTS, " $.data.token ", null)),
                        List.of(new ExtractionRule("token", " $.data.token ")),
                        true));

        ArgumentCaptor<TestCaseEntity> captor = ArgumentCaptor.forClass(TestCaseEntity.class);
        verify(testCaseMapper).insert(captor.capture());
        TestCaseEntity inserted = captor.getValue();
        assertThat(inserted.getName()).isEqualTo("Successful login");
        assertThat(inserted.getRequestBodyJson()).contains("admin");
        assertThat(inserted.getAssertionsJson()).contains("JSON_PATH_EXISTS");
        assertThat(inserted.getExtractionRulesJson()).contains("token");
        assertThat(response.assertions()).hasSize(2);
        assertThat(response.extractionRules()).singleElement()
                .extracting(ExtractionRule::name)
                .isEqualTo("token");
    }

    @Test
    void listDoesNotRevealAnotherUsersProject() {
        when(projectMapper.findByIdAndUserId(20L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testCaseService.list(8L, 20L, 30L))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiException.getCode()).isEqualTo("PROJECT_NOT_FOUND");
                });
        verifyNoInteractions(apiMapper, testCaseMapper);
    }

    @Test
    void createRejectsInvalidStatusAssertionBeforeInsert() {
        allowOwnedApi();
        when(testCaseMapper.findByNameOwned(20L, 30L, 7L, "Invalid assertion"))
                .thenReturn(Optional.empty());

        CreateTestCaseRequest request = new CreateTestCaseRequest(
                "Invalid assertion",
                null,
                TestCaseType.NEGATIVE,
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                List.of(new TestAssertion(AssertionType.STATUS_CODE, null, IntNode.valueOf(99))),
                List.of(),
                true);

        assertThatThrownBy(() -> testCaseService.create(7L, 20L, 30L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(exception ->
                        assertThat(((ApiException) exception).getCode()).isEqualTo("INVALID_ASSERTION"));
        verify(testCaseMapper, never()).insert(any(TestCaseEntity.class));
    }

    @Test
    void updateRejectsStaleVersion() {
        allowOwnedApi();
        TestCaseEntity stored = storedTestCase();
        when(testCaseMapper.findByIdOwned(40L, 20L, 30L, 7L)).thenReturn(Optional.of(stored));
        when(testCaseMapper.findByNameOwned(20L, 30L, 7L, "Successful login"))
                .thenReturn(Optional.of(stored));
        when(testCaseMapper.updateOwned(
                eq(40L), eq(20L), eq(30L), eq(7L), eq(1), any(TestCaseEntity.class)))
                .thenReturn(0);

        UpdateTestCaseRequest request = new UpdateTestCaseRequest(
                "Successful login",
                "Changed",
                TestCaseType.NORMAL,
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                List.of(new TestAssertion(AssertionType.BODY_CONTAINS, null, TextNode.valueOf("success"))),
                List.of(),
                true,
                1);

        assertThatThrownBy(() -> testCaseService.update(7L, 20L, 30L, 40L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiException.getCode()).isEqualTo("STALE_TEST_CASE_VERSION");
                });
    }

    private void allowOwnedApi() {
        when(projectMapper.findByIdAndUserId(20L, 7L)).thenReturn(Optional.of(project()));
        when(apiMapper.findByIdOwned(30L, 20L, 7L)).thenReturn(Optional.of(api()));
    }

    private static ProjectEntity project() {
        ProjectEntity project = new ProjectEntity();
        project.setId(20L);
        project.setUserId(7L);
        project.setName("Auth APIs");
        return project;
    }

    private static ApiEntity api() {
        ApiEntity api = new ApiEntity();
        api.setId(30L);
        api.setProjectId(20L);
        api.setMethod("POST");
        api.setPath("/login");
        return api;
    }

    private static TestCaseEntity storedTestCase() {
        TestCaseEntity testCase = new TestCaseEntity();
        testCase.setId(40L);
        testCase.setApiId(30L);
        testCase.setName("Successful login");
        testCase.setDescription("Returns a token");
        testCase.setType("NORMAL");
        testCase.setRequestHeadersJson("{\"Content-Type\":\"application/json\"}");
        testCase.setPathParametersJson("{}");
        testCase.setQueryParametersJson("{\"trace\":\"{{trace_id}}\"}");
        testCase.setRequestBodyJson("{\"username\":\"admin\"}");
        testCase.setAssertionsJson("[{\"type\":\"STATUS_CODE\",\"expected\":200},{\"type\":\"JSON_PATH_EXISTS\",\"expression\":\"$.data.token\"}]");
        testCase.setExtractionRulesJson("[{\"name\":\"token\",\"jsonPath\":\"$.data.token\"}]");
        testCase.setEnabled(true);
        testCase.setVersion(1);
        return testCase;
    }
}
