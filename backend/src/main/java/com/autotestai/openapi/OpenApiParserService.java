package com.autotestai.openapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.autotestai.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OpenApiParserService {

    private static final int MAX_PARSER_WARNINGS = 50;

    private final ObjectMapper objectMapper;

    public OpenApiParserService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedOpenApiDocument parse(String contents) {
        if (contents == null || contents.isBlank()) {
            throw invalidDocument("OpenAPI document is empty");
        }

        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        options.setFlatten(false);
        SwaggerParseResult result = new OpenAPIParser().readContents(contents, null, options);
        OpenAPI openApi = result.getOpenAPI();
        if (openApi == null || openApi.getPaths() == null) {
            throw invalidDocument(firstMessageOrDefault(result, "OpenAPI document could not be parsed"));
        }

        List<ParsedApiDefinition> definitions = new ArrayList<>();
        openApi.getPaths().forEach((path, pathItem) -> {
            if (pathItem == null) {
                return;
            }
            pathItem.readOperationsMap().forEach((method, operation) ->
                    definitions.add(toDefinition(openApi, path, pathItem, method, operation)));
        });
        if (definitions.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OPENAPI_NO_OPERATIONS",
                    "OpenAPI document does not contain any API operations");
        }

        String title = openApi.getInfo() == null ? null : trimToNull(openApi.getInfo().getTitle());
        String version = openApi.getInfo() == null ? null : trimToNull(openApi.getInfo().getVersion());
        List<String> warnings = result.getMessages() == null
                ? List.of()
                : result.getMessages().stream()
                        .filter(message -> message != null && !message.isBlank())
                        .distinct()
                        .limit(MAX_PARSER_WARNINGS)
                        .toList();
        return new ParsedOpenApiDocument(title, version, List.copyOf(definitions), warnings);
    }

    private ParsedApiDefinition toDefinition(
            OpenAPI openApi,
            String path,
            PathItem pathItem,
            PathItem.HttpMethod method,
            Operation operation) {
        List<Parameter> parameters = mergeParameters(pathItem.getParameters(), operation.getParameters());
        Object security = operation.getSecurity() == null ? openApi.getSecurity() : operation.getSecurity();
        return new ParsedApiDefinition(
                trimToNull(operation.getOperationId()),
                method.name(),
                path,
                trimToNull(operation.getSummary()),
                trimToNull(operation.getDescription()),
                writeJson(operation.getTags() == null ? List.of() : operation.getTags()),
                writeJson(parameters),
                writeJson(operation.getRequestBody()),
                writeJson(operation.getResponses()),
                writeJson(security));
    }

    private static List<Parameter> mergeParameters(
            List<Parameter> pathParameters,
            List<Parameter> operationParameters) {
        Map<String, Parameter> merged = new LinkedHashMap<>();
        if (pathParameters != null) {
            pathParameters.forEach(parameter -> merged.put(parameterKey(parameter), parameter));
        }
        if (operationParameters != null) {
            operationParameters.forEach(parameter -> merged.put(parameterKey(parameter), parameter));
        }
        return List.copyOf(merged.values());
    }

    private static String parameterKey(Parameter parameter) {
        if (parameter.get$ref() != null) {
            return "$ref:" + parameter.get$ref();
        }
        return String.valueOf(parameter.getIn()) + ":" + String.valueOf(parameter.getName());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalidDocument("OpenAPI document contains an unsupported schema value");
        }
    }

    private static String firstMessageOrDefault(SwaggerParseResult result, String fallback) {
        if (result.getMessages() == null) {
            return fallback;
        }
        return result.getMessages().stream()
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException invalidDocument(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OPENAPI_DOCUMENT", message);
    }
}
