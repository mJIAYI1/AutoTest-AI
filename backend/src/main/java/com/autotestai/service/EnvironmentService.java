package com.autotestai.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.autotestai.dto.environment.CreateEnvironmentRequest;
import com.autotestai.dto.environment.EnvironmentResponse;
import com.autotestai.dto.environment.UpdateEnvironmentRequest;
import com.autotestai.entity.EnvironmentEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.EnvironmentMapper;
import com.autotestai.mapper.ProjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentService {

    private static final Pattern HEADER_NAME =
            Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final Pattern VARIABLE_NAME =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]{0,119}$");
    private static final int MAX_HEADER_NAME_LENGTH = 100;
    private static final int MAX_HEADER_VALUE_LENGTH = 8192;
    private static final int MAX_VARIABLE_VALUE_LENGTH = 10000;
    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP =
            new TypeReference<>() {
            };

    private final ProjectMapper projectMapper;
    private final EnvironmentMapper environmentMapper;
    private final ObjectMapper objectMapper;

    public EnvironmentService(
            ProjectMapper projectMapper,
            EnvironmentMapper environmentMapper,
            ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.environmentMapper = environmentMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EnvironmentResponse create(long userId, long projectId, CreateEnvironmentRequest request) {
        requireOwnedProject(projectId, userId);
        String name = request.name().trim();
        ensureNameAvailable(projectId, userId, name, null);

        EnvironmentEntity environment = new EnvironmentEntity();
        environment.setProjectId(projectId);
        environment.setName(name);
        environment.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        environment.setHeadersJson(writeConfig(validateHeaders(request.headers())));
        environment.setVariablesJson(writeConfig(validateVariables(request.variables())));

        try {
            environmentMapper.insert(environment);
        } catch (DuplicateKeyException exception) {
            throw environmentNameTaken();
        }
        return toResponse(requireOwnedEnvironment(environment.getId(), projectId, userId));
    }

    @Transactional(readOnly = true)
    public List<EnvironmentResponse> list(long userId, long projectId) {
        requireOwnedProject(projectId, userId);
        return environmentMapper.findAllOwned(projectId, userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnvironmentResponse get(long userId, long projectId, long environmentId) {
        requireOwnedProject(projectId, userId);
        return toResponse(requireOwnedEnvironment(environmentId, projectId, userId));
    }

    @Transactional
    public EnvironmentResponse update(
            long userId,
            long projectId,
            long environmentId,
            UpdateEnvironmentRequest request) {
        requireOwnedProject(projectId, userId);
        requireOwnedEnvironment(environmentId, projectId, userId);
        String name = request.name().trim();
        ensureNameAvailable(projectId, userId, name, environmentId);

        String baseUrl = normalizeBaseUrl(request.baseUrl());
        String headersJson = writeConfig(validateHeaders(request.headers()));
        String variablesJson = writeConfig(validateVariables(request.variables()));
        try {
            int updated = environmentMapper.updateOwned(
                    environmentId,
                    projectId,
                    userId,
                    name,
                    baseUrl,
                    headersJson,
                    variablesJson);
            if (updated == 0) {
                throw environmentNotFound();
            }
        } catch (DuplicateKeyException exception) {
            throw environmentNameTaken();
        }
        return toResponse(requireOwnedEnvironment(environmentId, projectId, userId));
    }

    @Transactional
    public void delete(long userId, long projectId, long environmentId) {
        requireOwnedProject(projectId, userId);
        if (environmentMapper.deleteOwned(environmentId, projectId, userId) == 0) {
            throw environmentNotFound();
        }
    }

    private void requireOwnedProject(long projectId, long userId) {
        projectMapper.findByIdAndUserId(projectId, userId)
                .orElseThrow(EnvironmentService::projectNotFound);
    }

    private EnvironmentEntity requireOwnedEnvironment(long environmentId, long projectId, long userId) {
        return environmentMapper.findByIdOwned(environmentId, projectId, userId)
                .orElseThrow(EnvironmentService::environmentNotFound);
    }

    private void ensureNameAvailable(long projectId, long userId, String name, Long currentEnvironmentId) {
        environmentMapper.findByNameOwned(projectId, userId, name)
                .filter(environment -> currentEnvironmentId == null
                        || !environment.getId().equals(currentEnvironmentId))
                .ifPresent(environment -> {
                    throw environmentNameTaken();
                });
    }

    private EnvironmentResponse toResponse(EnvironmentEntity environment) {
        return new EnvironmentResponse(
                environment.getId(),
                environment.getProjectId(),
                environment.getName(),
                environment.getBaseUrl(),
                readConfig(environment.getHeadersJson()),
                readConfig(environment.getVariablesJson()),
                environment.getCreatedAt(),
                environment.getUpdatedAt());
    }

    private Map<String, String> validateHeaders(Map<String, String> headers) {
        Map<String, String> normalized = copyOrEmpty(headers);
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null
                    || name.length() > MAX_HEADER_NAME_LENGTH
                    || !HEADER_NAME.matcher(name).matches()
                    || value == null
                    || value.length() > MAX_HEADER_VALUE_LENGTH
                    || value.indexOf('\r') >= 0
                    || value.indexOf('\n') >= 0) {
                throw invalidEnvironmentConfig(
                        "Headers must use valid HTTP names and values without line breaks");
            }
        }
        return normalized;
    }

    private Map<String, String> validateVariables(Map<String, String> variables) {
        Map<String, String> normalized = copyOrEmpty(variables);
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null
                    || !VARIABLE_NAME.matcher(name).matches()
                    || value == null
                    || value.length() > MAX_VARIABLE_VALUE_LENGTH) {
                throw invalidEnvironmentConfig(
                        "Variable names must be template-safe identifiers and values must be strings");
            }
        }
        return normalized;
    }

    private static Map<String, String> copyOrEmpty(Map<String, String> value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyMap();
        }
        return new LinkedHashMap<>(value);
    }

    private String writeConfig(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalidEnvironmentConfig("Environment configuration could not be encoded as JSON");
        }
    }

    private Map<String, String> readConfig(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return Collections.unmodifiableMap(objectMapper.readValue(value, STRING_MAP));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored environment configuration is invalid", exception);
        }
    }

    private static String normalizeBaseUrl(String value) {
        String baseUrl = value == null ? null : value.trim();
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw invalidBaseUrl();
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

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project was not found");
    }

    private static ApiException environmentNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "ENVIRONMENT_NOT_FOUND",
                "Environment was not found");
    }

    private static ApiException environmentNameTaken() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "ENVIRONMENT_NAME_TAKEN",
                "An environment with this name already exists in the project");
    }

    private static ApiException invalidBaseUrl() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_BASE_URL",
                "Base URL must be an absolute HTTP or HTTPS URL without credentials or fragment");
    }

    private static ApiException invalidEnvironmentConfig(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ENVIRONMENT_CONFIG", message);
    }
}
