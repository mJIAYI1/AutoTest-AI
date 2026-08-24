package com.autotestai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.autotestai.config.ExecutionProperties;
import com.autotestai.dto.testsuite.CreateTestSuiteRequest;
import com.autotestai.dto.testsuite.TestSuiteCaseRequest;
import com.autotestai.entity.ProjectEntity;
import com.autotestai.entity.TestSuiteCaseEntity;
import com.autotestai.entity.TestSuiteEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.ProjectMapper;
import com.autotestai.mapper.TestSuiteMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TestSuiteServiceTest {

    private ProjectMapper projectMapper;
    private TestSuiteMapper testSuiteMapper;

    @BeforeEach
    void setUp() {
        projectMapper = mock(ProjectMapper.class);
        testSuiteMapper = mock(TestSuiteMapper.class);
        ProjectEntity project = new ProjectEntity();
        project.setId(7L);
        when(projectMapper.findByIdAndUserId(7, 3)).thenReturn(Optional.of(project));
    }

    @Test
    void createsSuiteCasesInRequestOrder() {
        TestSuiteService service = service(5);
        when(testSuiteMapper.findByNameOwned(7, 3, "Purchase flow"))
                .thenReturn(Optional.empty());
        when(testSuiteMapper.findCandidateOwned(11, 7, 3))
                .thenReturn(Optional.of(candidate(11, 101, "Login")));
        when(testSuiteMapper.findCandidateOwned(22, 7, 3))
                .thenReturn(Optional.of(candidate(22, 202, "Create order")));
        when(testSuiteMapper.insert(any(TestSuiteEntity.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, TestSuiteEntity.class).setId(9L);
            return 1;
        });

        TestSuiteEntity stored = new TestSuiteEntity();
        stored.setId(9L);
        stored.setProjectId(7L);
        stored.setName("Purchase flow");
        stored.setStopOnFailure(true);
        stored.setStatus("ACTIVE");
        stored.setVersion(1);
        stored.setCreatedAt(LocalDateTime.now());
        stored.setUpdatedAt(LocalDateTime.now());
        when(testSuiteMapper.findByIdOwned(9, 7, 3)).thenReturn(Optional.of(stored));

        List<TestSuiteCaseEntity> persisted = new ArrayList<>();
        when(testSuiteMapper.insertCase(any(TestSuiteCaseEntity.class))).thenAnswer(invocation -> {
            persisted.add(invocation.getArgument(0, TestSuiteCaseEntity.class));
            return 1;
        });
        when(testSuiteMapper.findCasesOwned(9, 7, 3)).thenAnswer(invocation -> persisted);

        var response = service.create(3, 7, new CreateTestSuiteRequest(
                " Purchase flow ",
                "ordered workflow",
                true,
                List.of(
                        new TestSuiteCaseRequest(11L, true),
                        new TestSuiteCaseRequest(22L, false))));

        assertThat(response.name()).isEqualTo("Purchase flow");
        assertThat(response.cases()).extracting(item -> item.testCaseId())
                .containsExactly(11L, 22L);
        assertThat(response.cases()).extracting(item -> item.sortOrder())
                .containsExactly(1, 2);
        assertThat(response.cases()).extracting(item -> item.enabled())
                .containsExactly(true, false);
        verify(testSuiteMapper).deleteCases(9);
    }

    @Test
    void rejectsDuplicateCases() {
        TestSuiteService service = service(5);
        when(testSuiteMapper.findByNameOwned(7, 3, "Duplicate"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(3, 7, new CreateTestSuiteRequest(
                "Duplicate",
                null,
                false,
                List.of(
                        new TestSuiteCaseRequest(11L, true),
                        new TestSuiteCaseRequest(11L, true)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("only once");
    }

    @Test
    void appliesConfiguredMaximumSuiteSize() {
        TestSuiteService service = service(2);
        when(testSuiteMapper.findByNameOwned(7, 3, "Too large"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(3, 7, new CreateTestSuiteRequest(
                "Too large",
                null,
                false,
                List.of(
                        new TestSuiteCaseRequest(11L, true),
                        new TestSuiteCaseRequest(22L, true),
                        new TestSuiteCaseRequest(33L, true)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at most 2");
    }

    private TestSuiteService service(int maxSuiteCases) {
        ExecutionProperties properties = new ExecutionProperties(
                Set.of("localhost"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                1_048_576,
                1,
                2,
                10,
                maxSuiteCases);
        return new TestSuiteService(projectMapper, testSuiteMapper, properties);
    }

    private static TestSuiteCaseEntity candidate(long caseId, long apiId, String name) {
        TestSuiteCaseEntity candidate = new TestSuiteCaseEntity();
        candidate.setTestCaseId(caseId);
        candidate.setApiId(apiId);
        candidate.setTestCaseName(name);
        candidate.setTestCaseEnabled(true);
        candidate.setMethod("POST");
        candidate.setPath("/" + name.toLowerCase().replace(' ', '-'));
        return candidate;
    }
}
