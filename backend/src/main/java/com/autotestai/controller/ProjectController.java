package com.autotestai.controller;

import java.util.List;

import com.autotestai.dto.project.CreateProjectRequest;
import com.autotestai.dto.project.ProjectResponse;
import com.autotestai.dto.project.UpdateProjectRequest;
import com.autotestai.security.CurrentUserId;
import com.autotestai.service.ProjectService;
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
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(CurrentUserId.from(jwt), request);
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return projectService.list(CurrentUserId.from(jwt));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId) {
        return projectService.get(CurrentUserId.from(jwt), projectId);
    }

    @PutMapping("/{projectId}")
    public ProjectResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(CurrentUserId.from(jwt), projectId, request);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId) {
        projectService.delete(CurrentUserId.from(jwt), projectId);
    }
}
