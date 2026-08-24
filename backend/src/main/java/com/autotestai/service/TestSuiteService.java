package com.autotestai.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.autotestai.config.ExecutionProperties;
import com.autotestai.dto.testsuite.CreateTestSuiteRequest;
import com.autotestai.dto.testsuite.TestSuiteCaseCandidateResponse;
import com.autotestai.dto.testsuite.TestSuiteCaseRequest;
import com.autotestai.dto.testsuite.TestSuiteCaseResponse;
import com.autotestai.dto.testsuite.TestSuiteResponse;
import com.autotestai.dto.testsuite.UpdateTestSuiteRequest;
import com.autotestai.entity.TestSuiteCaseEntity;
import com.autotestai.entity.TestSuiteEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.ProjectMapper;
import com.autotestai.mapper.TestSuiteMapper;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestSuiteService {

    private final ProjectMapper projectMapper;
    private final TestSuiteMapper testSuiteMapper;
    private final ExecutionProperties executionProperties;

    public TestSuiteService(
            ProjectMapper projectMapper,
            TestSuiteMapper testSuiteMapper,
            ExecutionProperties executionProperties) {
        this.projectMapper = projectMapper;
        this.testSuiteMapper = testSuiteMapper;
        this.executionProperties = executionProperties;
    }

    @Transactional
    public TestSuiteResponse create(
            long userId,
            long projectId,
            CreateTestSuiteRequest request) {
        requireOwnedProject(userId, projectId);
        String name = request.name().trim();
        ensureNameAvailable(projectId, userId, name, null);
        List<TestSuiteCaseEntity> cases = validateCases(
                userId, projectId, request.cases());

        TestSuiteEntity suite = new TestSuiteEntity();
        suite.setProjectId(projectId);
        suite.setName(name);
        suite.setDescription(normalizeNullable(request.description()));
        suite.setStopOnFailure(request.stopOnFailure());
        try {
            testSuiteMapper.insert(suite);
            replaceCases(suite.getId(), cases);
        } catch (DuplicateKeyException exception) {
            throw suiteNameTaken();
        }
        return toResponse(requireOwnedSuite(suite.getId(), projectId, userId), userId);
    }

    @Transactional(readOnly = true)
    public List<TestSuiteResponse> list(long userId, long projectId) {
        requireOwnedProject(userId, projectId);
        return testSuiteMapper.findAllOwned(projectId, userId).stream()
                .map(suite -> toResponse(suite, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public TestSuiteResponse get(long userId, long projectId, long suiteId) {
        requireOwnedProject(userId, projectId);
        return toResponse(requireOwnedSuite(suiteId, projectId, userId), userId);
    }

    @Transactional(readOnly = true)
    public List<TestSuiteCaseCandidateResponse> candidates(long userId, long projectId) {
        requireOwnedProject(userId, projectId);
        return testSuiteMapper.findCandidatesOwned(projectId, userId).stream()
                .map(item -> new TestSuiteCaseCandidateResponse(
                        item.getTestCaseId(),
                        item.getTestCaseName(),
                        Boolean.TRUE.equals(item.getTestCaseEnabled()),
                        item.getApiId(),
                        item.getMethod(),
                        item.getPath()))
                .toList();
    }

    @Transactional
    public TestSuiteResponse update(
            long userId,
            long projectId,
            long suiteId,
            UpdateTestSuiteRequest request) {
        requireOwnedProject(userId, projectId);
        requireOwnedSuite(suiteId, projectId, userId);
        String name = request.name().trim();
        ensureNameAvailable(projectId, userId, name, suiteId);
        List<TestSuiteCaseEntity> cases = validateCases(
                userId, projectId, request.cases());

        TestSuiteEntity suite = new TestSuiteEntity();
        suite.setName(name);
        suite.setDescription(normalizeNullable(request.description()));
        suite.setStopOnFailure(request.stopOnFailure());
        try {
            if (testSuiteMapper.updateOwned(
                    suiteId, projectId, userId, request.version(), suite) == 0) {
                throw staleVersion();
            }
            replaceCases(suiteId, cases);
        } catch (DuplicateKeyException exception) {
            throw suiteNameTaken();
        }
        return toResponse(requireOwnedSuite(suiteId, projectId, userId), userId);
    }

    @Transactional
    public void delete(long userId, long projectId, long suiteId) {
        requireOwnedProject(userId, projectId);
        requireOwnedSuite(suiteId, projectId, userId);
        try {
            if (testSuiteMapper.deleteOwned(suiteId, projectId, userId) == 0) {
                throw suiteNotFound();
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TEST_SUITE_HAS_RUN_HISTORY",
                    "Test suites with run history cannot be deleted");
        }
    }

    private List<TestSuiteCaseEntity> validateCases(
            long userId,
            long projectId,
            List<TestSuiteCaseRequest> requestedCases) {
        if (requestedCases.size() > executionProperties.maxSuiteCases()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TEST_SUITE_TOO_LARGE",
                    "A test suite can contain at most "
                            + executionProperties.maxSuiteCases() + " cases");
        }
        Set<Long> seen = new HashSet<>();
        for (TestSuiteCaseRequest requested : requestedCases) {
            if (!seen.add(requested.testCaseId())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_TEST_SUITE_CASE",
                        "A test case can appear only once in a test suite");
            }
        }
        return java.util.stream.IntStream.range(0, requestedCases.size())
                .mapToObj(index -> {
                    TestSuiteCaseRequest requested = requestedCases.get(index);
                    TestSuiteCaseEntity owned = testSuiteMapper.findCandidateOwned(
                            requested.testCaseId(), projectId, userId)
                            .orElseThrow(TestSuiteService::testCaseNotFound);
                    owned.setSortOrder(index + 1);
                    owned.setEnabled(requested.enabled());
                    return owned;
                })
                .toList();
    }

    private void replaceCases(long suiteId, List<TestSuiteCaseEntity> cases) {
        testSuiteMapper.deleteCases(suiteId);
        for (TestSuiteCaseEntity suiteCase : cases) {
            suiteCase.setTestSuiteId(suiteId);
            testSuiteMapper.insertCase(suiteCase);
        }
    }

    private TestSuiteResponse toResponse(TestSuiteEntity suite, long userId) {
        List<TestSuiteCaseResponse> cases = testSuiteMapper.findCasesOwned(
                suite.getId(), suite.getProjectId(), userId).stream()
                .map(item -> new TestSuiteCaseResponse(
                        item.getTestCaseId(),
                        item.getSortOrder(),
                        Boolean.TRUE.equals(item.getEnabled()),
                        item.getTestCaseName(),
                        Boolean.TRUE.equals(item.getTestCaseEnabled()),
                        item.getApiId(),
                        item.getMethod(),
                        item.getPath()))
                .toList();
        return new TestSuiteResponse(
                suite.getId(),
                suite.getProjectId(),
                suite.getName(),
                suite.getDescription(),
                Boolean.TRUE.equals(suite.getStopOnFailure()),
                suite.getStatus(),
                suite.getVersion(),
                cases,
                suite.getCreatedAt(),
                suite.getUpdatedAt());
    }

    private void requireOwnedProject(long userId, long projectId) {
        if (projectMapper.findByIdAndUserId(projectId, userId).isEmpty()) {
            throw projectNotFound();
        }
    }

    private TestSuiteEntity requireOwnedSuite(long suiteId, long projectId, long userId) {
        return testSuiteMapper.findByIdOwned(suiteId, projectId, userId)
                .orElseThrow(TestSuiteService::suiteNotFound);
    }

    private void ensureNameAvailable(
            long projectId,
            long userId,
            String name,
            Long currentSuiteId) {
        testSuiteMapper.findByNameOwned(projectId, userId, name)
                .filter(suite -> currentSuiteId == null || !suite.getId().equals(currentSuiteId))
                .ifPresent(suite -> {
                    throw suiteNameTaken();
                });
    }

    private static String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project was not found");
    }

    private static ApiException suiteNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TEST_SUITE_NOT_FOUND", "Test suite was not found");
    }

    private static ApiException testCaseNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "TEST_CASE_NOT_FOUND",
                "One or more test cases were not found in this project");
    }

    private static ApiException suiteNameTaken() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "TEST_SUITE_NAME_TAKEN",
                "A test suite with this name already exists in the project");
    }

    private static ApiException staleVersion() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "STALE_TEST_SUITE_VERSION",
                "The test suite was modified by another request; reload and try again");
    }
}
