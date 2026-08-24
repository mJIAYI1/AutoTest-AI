package com.autotestai.controller;

import java.util.List;

import com.autotestai.dto.environment.CreateEnvironmentRequest;
import com.autotestai.dto.environment.EnvironmentResponse;
import com.autotestai.dto.environment.UpdateEnvironmentRequest;
import com.autotestai.security.CurrentUserId;
import com.autotestai.service.EnvironmentService;
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
@RequestMapping("/api/projects/{projectId}/environments")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @Valid @RequestBody CreateEnvironmentRequest request) {
        return environmentService.create(CurrentUserId.from(jwt), projectId, request);
    }

    @GetMapping
    public List<EnvironmentResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId) {
        return environmentService.list(CurrentUserId.from(jwt), projectId);
    }

    @GetMapping("/{environmentId}")
    public EnvironmentResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long environmentId) {
        return environmentService.get(CurrentUserId.from(jwt), projectId, environmentId);
    }

    @PutMapping("/{environmentId}")
    public EnvironmentResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long environmentId,
            @Valid @RequestBody UpdateEnvironmentRequest request) {
        return environmentService.update(
                CurrentUserId.from(jwt), projectId, environmentId, request);
    }

    @DeleteMapping("/{environmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long environmentId) {
        environmentService.delete(CurrentUserId.from(jwt), projectId, environmentId);
    }
}
