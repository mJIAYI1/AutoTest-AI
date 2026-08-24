package com.autotestai.controller;

import java.util.List;

import com.autotestai.dto.testcase.CreateTestCaseRequest;
import com.autotestai.dto.testcase.TestCaseResponse;
import com.autotestai.dto.testcase.UpdateTestCaseRequest;
import com.autotestai.security.CurrentUserId;
import com.autotestai.service.TestCaseService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/apis/{apiId}/test-cases")
public class TestCaseController {

    private final TestCaseService testCaseService;

    public TestCaseController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestCaseResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long apiId,
            @Valid @RequestBody CreateTestCaseRequest request) {
        return testCaseService.create(CurrentUserId.from(jwt), projectId, apiId, request);
    }

    @GetMapping
    public List<TestCaseResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long apiId) {
        return testCaseService.list(CurrentUserId.from(jwt), projectId, apiId);
    }

    @GetMapping("/{testCaseId}")
    public TestCaseResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long apiId,
            @PathVariable long testCaseId) {
        return testCaseService.get(CurrentUserId.from(jwt), projectId, apiId, testCaseId);
    }

    @PutMapping("/{testCaseId}")
    public TestCaseResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long apiId,
            @PathVariable long testCaseId,
            @Valid @RequestBody UpdateTestCaseRequest request) {
        return testCaseService.update(
                CurrentUserId.from(jwt), projectId, apiId, testCaseId, request);
    }

    @DeleteMapping("/{testCaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long apiId,
            @PathVariable long testCaseId) {
        testCaseService.delete(CurrentUserId.from(jwt), projectId, apiId, testCaseId);
    }
}
