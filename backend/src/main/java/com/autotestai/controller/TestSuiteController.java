package com.autotestai.controller;

import java.util.List;

import com.autotestai.dto.testsuite.CreateTestSuiteRequest;
import com.autotestai.dto.testsuite.TestSuiteCaseCandidateResponse;
import com.autotestai.dto.testsuite.TestSuiteResponse;
import com.autotestai.dto.testsuite.UpdateTestSuiteRequest;
import com.autotestai.security.CurrentUserId;
import com.autotestai.service.TestSuiteService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestSuiteController {

    private final TestSuiteService testSuiteService;

    public TestSuiteController(TestSuiteService testSuiteService) {
        this.testSuiteService = testSuiteService;
    }

    @PostMapping("/api/projects/{projectId}/test-suites")
    @ResponseStatus(HttpStatus.CREATED)
    public TestSuiteResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @Valid @RequestBody CreateTestSuiteRequest request) {
        return testSuiteService.create(CurrentUserId.from(jwt), projectId, request);
    }

    @GetMapping("/api/projects/{projectId}/test-suites")
    public List<TestSuiteResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId) {
        return testSuiteService.list(CurrentUserId.from(jwt), projectId);
    }

    @GetMapping("/api/projects/{projectId}/test-suites/candidates")
    public List<TestSuiteCaseCandidateResponse> candidates(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId) {
        return testSuiteService.candidates(CurrentUserId.from(jwt), projectId);
    }

    @GetMapping("/api/projects/{projectId}/test-suites/{suiteId}")
    public TestSuiteResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long suiteId) {
        return testSuiteService.get(CurrentUserId.from(jwt), projectId, suiteId);
    }

    @PutMapping("/api/projects/{projectId}/test-suites/{suiteId}")
    public TestSuiteResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long suiteId,
            @Valid @RequestBody UpdateTestSuiteRequest request) {
        return testSuiteService.update(CurrentUserId.from(jwt), projectId, suiteId, request);
    }

    @DeleteMapping("/api/projects/{projectId}/test-suites/{suiteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long suiteId) {
        testSuiteService.delete(CurrentUserId.from(jwt), projectId, suiteId);
    }
}
