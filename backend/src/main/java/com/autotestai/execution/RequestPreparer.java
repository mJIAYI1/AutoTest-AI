package com.autotestai.execution;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

@Component
public class RequestPreparer {

    private static final Pattern UNRESOLVED_PATH_PARAMETER = Pattern.compile("\\{[^/{}]+}");
    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final TemplateResolver templateResolver;
    private final TargetUrlPolicy targetUrlPolicy;

    public RequestPreparer(
            ObjectMapper objectMapper,
            TemplateResolver templateResolver,
            TargetUrlPolicy targetUrlPolicy) {
        this.objectMapper = objectMapper;
        this.templateResolver = templateResolver;
        this.targetUrlPolicy = targetUrlPolicy;
    }

    public PreparedRequest prepare(TestExecutionSnapshot snapshot) {
        return prepare(snapshot, Map.of());
    }

    public PreparedRequest prepare(
            TestExecutionSnapshot snapshot,
            Map<String, String> runtimeVariables) {
        Map<String, String> variables = new LinkedHashMap<>();
        if (snapshot.environment() != null) {
            variables.putAll(readMap(
                    snapshot.environment().getVariablesJson(), "environment variables"));
        }
        if (runtimeVariables != null) {
            variables.putAll(runtimeVariables);
        }
        String baseUrl = snapshot.environment() != null
                ? snapshot.environment().getBaseUrl()
                : snapshot.project().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ExecutionException("No Base URL is configured for this run");
        }

        Map<String, String> pathParameters = readMap(
                snapshot.testCase().getPathParametersJson(), "path parameters");
        String path = templateResolver.resolve(snapshot.api().getPath(), variables);
        for (Map.Entry<String, String> entry : pathParameters.entrySet()) {
            String value = templateResolver.resolve(entry.getValue(), variables);
            path = path.replace(
                    "{" + entry.getKey() + "}",
                    UriUtils.encodePathSegment(value, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (UNRESOLVED_PATH_PARAMETER.matcher(path).find()) {
            throw new ExecutionException("One or more required path parameters are missing");
        }

        String target = joinBaseAndPath(baseUrl, path);
        Map<String, String> queryParameters = readMap(
                snapshot.testCase().getQueryParametersJson(), "query parameters");
        if (!queryParameters.isEmpty()) {
            StringBuilder query = new StringBuilder();
            for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
                if (!query.isEmpty()) {
                    query.append('&');
                }
                query.append(UriUtils.encodeQueryParam(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8));
                query.append('=');
                query.append(UriUtils.encodeQueryParam(
                        templateResolver.resolve(entry.getValue(), variables),
                        java.nio.charset.StandardCharsets.UTF_8));
            }
            target += (target.contains("?") ? "&" : "?") + query;
        }

        URI uri;
        try {
            uri = URI.create(target);
        } catch (IllegalArgumentException exception) {
            throw new ExecutionException("Execution target URL is invalid", exception);
        }
        targetUrlPolicy.validate(uri);

        Map<String, String> mergedHeaders = new LinkedHashMap<>();
        if (snapshot.environment() != null) {
            mergedHeaders.putAll(readMap(snapshot.environment().getHeadersJson(), "environment headers"));
        }
        mergedHeaders.putAll(readMap(snapshot.testCase().getRequestHeadersJson(), "request headers"));
        Map<String, List<String>> headers = new LinkedHashMap<>();
        mergedHeaders.forEach((name, value) -> headers.put(
                name,
                List.of(templateResolver.resolve(value, variables))));

        String body = prepareBody(snapshot.testCase().getRequestBodyJson(), variables);
        if (body != null && headers.keySet().stream().noneMatch(HttpHeaders.CONTENT_TYPE::equalsIgnoreCase)) {
            headers.put(HttpHeaders.CONTENT_TYPE, List.of("application/json"));
        }

        HttpMethod method;
        try {
            method = HttpMethod.valueOf(snapshot.api().getMethod().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ExecutionException("Unsupported HTTP method: " + snapshot.api().getMethod());
        }
        return new PreparedRequest(uri, method, immutableHeaders(headers), body);
    }

    private String prepareBody(String storedBody, Map<String, String> variables) {
        if (storedBody == null || storedBody.isBlank() || storedBody.equals("null")) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(storedBody);
            return objectMapper.writeValueAsString(resolveJson(root, variables));
        } catch (JsonProcessingException exception) {
            throw new ExecutionException("Stored request body is invalid JSON", exception);
        }
    }

    private JsonNode resolveJson(JsonNode node, Map<String, String> variables) {
        if (node.isTextual()) {
            return TextNode.valueOf(templateResolver.resolve(node.textValue(), variables));
        }
        if (node.isObject()) {
            ObjectNode copy = ((ObjectNode) node).deepCopy();
            List<String> names = new ArrayList<>();
            copy.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> copy.set(name, resolveJson(copy.get(name), variables)));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = ((ArrayNode) node).deepCopy();
            for (int index = 0; index < copy.size(); index++) {
                copy.set(index, resolveJson(copy.get(index), variables));
            }
            return copy;
        }
        return node.deepCopy();
    }

    private Map<String, String> readMap(String value, String label) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, STRING_MAP);
        } catch (JsonProcessingException exception) {
            throw new ExecutionException("Stored " + label + " are invalid JSON", exception);
        }
    }

    private static String joinBaseAndPath(String baseUrl, String path) {
        URI base;
        try {
            base = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new ExecutionException("Configured Base URL is invalid", exception);
        }
        if (base.getQuery() != null) {
            throw new ExecutionException("Configured Base URL must not contain a query string");
        }
        String normalizedBase = base.toString();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        return normalizedBase + (path.startsWith("/") ? path : "/" + path);
    }

    private static Map<String, List<String>> immutableHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        headers.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }
}
