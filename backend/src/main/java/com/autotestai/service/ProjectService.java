package com.autotestai.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.autotestai.dto.project.CreateProjectRequest;
import com.autotestai.dto.project.ProjectResponse;
import com.autotestai.dto.project.UpdateProjectRequest;
import com.autotestai.entity.ProjectEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.ProjectMapper;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;

    public ProjectService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    @Transactional
    public ProjectResponse create(long userId, CreateProjectRequest request) {
        String name = normalizeName(request.name());
        ensureNameAvailable(userId, name, null);

        ProjectEntity project = new ProjectEntity();
        project.setUserId(userId);
        project.setName(name);
        project.setDescription(trimToNull(request.description()));
        project.setBaseUrl(normalizeBaseUrl(request.baseUrl()));

        try {
            projectMapper.insert(project);
        } catch (DuplicateKeyException exception) {
            throw projectNameTaken();
        }
        return ProjectResponse.from(requireOwnedProject(project.getId(), userId));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(long userId) {
        return projectMapper.findAllByUserId(userId).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(long userId, long projectId) {
        return ProjectResponse.from(requireOwnedProject(projectId, userId));
    }

    @Transactional
    public ProjectResponse update(long userId, long projectId, UpdateProjectRequest request) {
        requireOwnedProject(projectId, userId);
        String name = normalizeName(request.name());
        ensureNameAvailable(userId, name, projectId);

        try {
            int updated = projectMapper.updateOwned(
                    projectId,
                    userId,
                    name,
                    trimToNull(request.description()),
                    normalizeBaseUrl(request.baseUrl()));
            if (updated == 0) {
                throw projectNotFound();
            }
        } catch (DuplicateKeyException exception) {
            throw projectNameTaken();
        }
        return ProjectResponse.from(requireOwnedProject(projectId, userId));
    }

    @Transactional
    public void delete(long userId, long projectId) {
        if (projectMapper.deleteOwned(projectId, userId) == 0) {
            throw projectNotFound();
        }
    }

    private ProjectEntity requireOwnedProject(long projectId, long userId) {
        return projectMapper.findByIdAndUserId(projectId, userId)
                .orElseThrow(ProjectService::projectNotFound);
    }

    private void ensureNameAvailable(long userId, String name, Long currentProjectId) {
        projectMapper.findByUserIdAndName(userId, name)
                .filter(project -> currentProjectId == null || !project.getId().equals(currentProjectId))
                .ifPresent(project -> {
                    throw projectNameTaken();
                });
    }

    private static String normalizeName(String name) {
        return name.trim();
    }

    private static String normalizeBaseUrl(String value) {
        String baseUrl = trimToNull(value);
        if (baseUrl == null) {
            return null;
        }
        try {
            URI uri = new URI(baseUrl);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw invalidBaseUrl();
            }
            return uri.toString();
        } catch (URISyntaxException exception) {
            throw invalidBaseUrl();
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project was not found");
    }

    private static ApiException projectNameTaken() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "PROJECT_NAME_TAKEN",
                "A project with this name already exists for the current user");
    }

    private static ApiException invalidBaseUrl() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_BASE_URL",
                "Base URL must be an absolute HTTP or HTTPS URL without credentials or fragment");
    }
}
