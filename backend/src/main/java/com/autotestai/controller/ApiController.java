package com.autotestai.controller;

import java.util.List;

import com.autotestai.dto.openapi.ApiDefinitionResponse;
import com.autotestai.dto.openapi.OpenApiImportResponse;
import com.autotestai.dto.openapi.OpenApiUrlImportRequest;
import com.autotestai.security.CurrentUserId;
import com.autotestai.service.ApiCatalogService;
import com.autotestai.service.OpenApiImportService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/apis")
public class ApiController {

    private final OpenApiImportService importService;
    private final ApiCatalogService apiCatalogService;

    public ApiController(
            OpenApiImportService importService,
            ApiCatalogService apiCatalogService) {
        this.importService = importService;
        this.apiCatalogService = apiCatalogService;
    }

    @PostMapping("/import/url")
    @ResponseStatus(HttpStatus.CREATED)
    public OpenApiImportResponse importFromUrl(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @Valid @RequestBody OpenApiUrlImportRequest request) {
        return importService.importFromUrl(CurrentUserId.from(jwt), projectId, request.url());
    }

    @PostMapping(value = "/import/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public OpenApiImportResponse importFromFile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @RequestPart("file") MultipartFile file) {
        return importService.importFromFile(CurrentUserId.from(jwt), projectId, file);
    }

    @GetMapping
    public List<ApiDefinitionResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId) {
        return apiCatalogService.list(CurrentUserId.from(jwt), projectId);
    }

    @GetMapping("/{apiId}")
    public ApiDefinitionResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long projectId,
            @PathVariable long apiId) {
        return apiCatalogService.get(CurrentUserId.from(jwt), projectId, apiId);
    }
}
