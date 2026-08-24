package com.autotestai.service;

import java.util.List;

import com.autotestai.dto.openapi.ApiDefinitionResponse;
import com.autotestai.dto.openapi.OpenApiImportResponse;
import com.autotestai.entity.ApiEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.ApiMapper;
import com.autotestai.mapper.ProjectMapper;
import com.autotestai.openapi.ParsedApiDefinition;
import com.autotestai.openapi.ParsedOpenApiDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiCatalogService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ProjectMapper projectMapper;
    private final ApiMapper apiMapper;
    private final ObjectMapper objectMapper;

    public ApiCatalogService(
            ProjectMapper projectMapper,
            ApiMapper apiMapper,
            ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.apiMapper = apiMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public void ensureOwnedProject(long userId, long projectId) {
        requireOwnedProject(userId, projectId);
    }

    @Transactional
    public OpenApiImportResponse upsert(
            long userId,
            long projectId,
            ParsedOpenApiDocument document) {
        requireOwnedProject(userId, projectId);
        for (ParsedApiDefinition definition : document.definitions()) {
            validateDefinition(definition);
            apiMapper.upsert(toEntity(projectId, definition));
        }
        return new OpenApiImportResponse(
                projectId,
                document.title(),
                document.version(),
                document.definitions().size(),
                document.warnings());
    }

    @Transactional(readOnly = true)
    public List<ApiDefinitionResponse> list(long userId, long projectId) {
        requireOwnedProject(userId, projectId);
        return apiMapper.findAllOwned(projectId, userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiDefinitionResponse get(long userId, long projectId, long apiId) {
        requireOwnedProject(userId, projectId);
        ApiEntity api = apiMapper.findByIdOwned(apiId, projectId, userId)
                .orElseThrow(ApiCatalogService::apiNotFound);
        return toResponse(api);
    }

    private void requireOwnedProject(long userId, long projectId) {
        if (projectMapper.findByIdAndUserId(projectId, userId).isEmpty()) {
            throw projectNotFound();
        }
    }

    private static ApiEntity toEntity(long projectId, ParsedApiDefinition definition) {
        ApiEntity api = new ApiEntity();
        api.setProjectId(projectId);
        api.setOperationId(definition.operationId());
        api.setMethod(definition.method());
        api.setPath(definition.path());
        api.setSummary(definition.summary());
        api.setDescription(definition.description());
        api.setTagsJson(definition.tagsJson());
        api.setParametersJson(definition.parametersJson());
        api.setRequestSchemaJson(definition.requestSchemaJson());
        api.setResponseSchemaJson(definition.responseSchemaJson());
        api.setSecurityJson(definition.securityJson());
        return api;
    }

    private ApiDefinitionResponse toResponse(ApiEntity api) {
        return new ApiDefinitionResponse(
                api.getId(),
                api.getProjectId(),
                api.getOperationId(),
                api.getMethod(),
                api.getPath(),
                api.getSummary(),
                api.getDescription(),
                readTags(api.getTagsJson()),
                readJson(api.getParametersJson(), objectMapper.createArrayNode()),
                readJson(api.getRequestSchemaJson(), NullNode.getInstance()),
                readJson(api.getResponseSchemaJson(), objectMapper.createObjectNode()),
                readJson(api.getSecurityJson(), NullNode.getInstance()),
                api.getCreatedAt(),
                api.getUpdatedAt());
    }

    private List<String> readTags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(value, STRING_LIST));
        } catch (JsonProcessingException exception) {
            throw storedJsonInvalid(exception);
        }
    }

    private JsonNode readJson(String value, JsonNode defaultValue) {
        if (value == null || value.isBlank() || value.equals("null")) {
            return defaultValue;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node == null ? defaultValue : node;
        } catch (JsonProcessingException exception) {
            throw storedJsonInvalid(exception);
        }
    }

    private static void validateDefinition(ParsedApiDefinition definition) {
        if (definition.path() == null || definition.path().isBlank() || definition.path().length() > 512) {
            throw invalidDocument("An API path is missing or exceeds 512 characters");
        }
        if (definition.method() == null || definition.method().length() > 12) {
            throw invalidDocument("An API method is missing or invalid");
        }
        if (definition.operationId() != null && definition.operationId().length() > 255) {
            throw invalidDocument("Operation ID exceeds 255 characters for " + definition.path());
        }
        if (definition.summary() != null && definition.summary().length() > 500) {
            throw invalidDocument("Summary exceeds 500 characters for " + definition.path());
        }
        if (definition.description() != null
                && definition.description().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65_535) {
            throw invalidDocument("Description is too large for " + definition.path());
        }
    }

    private static IllegalStateException storedJsonInvalid(JsonProcessingException exception) {
        return new IllegalStateException("Stored API definition JSON is invalid", exception);
    }

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project was not found");
    }

    private static ApiException apiNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "API_NOT_FOUND", "API definition was not found");
    }

    private static ApiException invalidDocument(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OPENAPI_DOCUMENT", message);
    }
}
