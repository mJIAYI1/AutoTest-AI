package com.autotestai.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.autotestai.config.OpenApiImportProperties;
import com.autotestai.dto.openapi.OpenApiImportResponse;
import com.autotestai.exception.ApiException;
import com.autotestai.openapi.OpenApiDocumentFetcher;
import com.autotestai.openapi.OpenApiParserService;
import com.autotestai.openapi.ParsedOpenApiDocument;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OpenApiImportService {

    private final ApiCatalogService apiCatalogService;
    private final OpenApiDocumentFetcher documentFetcher;
    private final OpenApiParserService parserService;
    private final OpenApiImportProperties properties;

    public OpenApiImportService(
            ApiCatalogService apiCatalogService,
            OpenApiDocumentFetcher documentFetcher,
            OpenApiParserService parserService,
            OpenApiImportProperties properties) {
        this.apiCatalogService = apiCatalogService;
        this.documentFetcher = documentFetcher;
        this.parserService = parserService;
        this.properties = properties;
    }

    public OpenApiImportResponse importFromUrl(
            long userId,
            long projectId,
            String sourceUrl) {
        apiCatalogService.ensureOwnedProject(userId, projectId);
        String contents = documentFetcher.fetch(sourceUrl);
        ParsedOpenApiDocument document = parserService.parse(contents);
        return apiCatalogService.upsert(userId, projectId, document);
    }

    public OpenApiImportResponse importFromFile(
            long userId,
            long projectId,
            MultipartFile file) {
        apiCatalogService.ensureOwnedProject(userId, projectId);
        validateFile(file);
        ParsedOpenApiDocument document = parserService.parse(readFile(file));
        return apiCatalogService.upsert(userId, projectId, document);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidFile("OpenAPI file is missing or empty");
        }
        String filename = file.getOriginalFilename();
        String normalized = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!(normalized.endsWith(".json")
                || normalized.endsWith(".yaml")
                || normalized.endsWith(".yml"))) {
            throw invalidFile("OpenAPI file must use .json, .yaml, or .yml extension");
        }
        if (file.getSize() > properties.maxDocumentBytes()) {
            throw documentTooLarge();
        }
    }

    private String readFile(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(properties.maxDocumentBytes() + 1);
            if (bytes.length > properties.maxDocumentBytes()) {
                throw documentTooLarge();
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw invalidFile("OpenAPI file could not be read");
        }
    }

    private static ApiException invalidFile(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OPENAPI_FILE", message);
    }

    private static ApiException documentTooLarge() {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "OPENAPI_DOCUMENT_TOO_LARGE",
                "OpenAPI document exceeds the configured size limit");
    }
}
