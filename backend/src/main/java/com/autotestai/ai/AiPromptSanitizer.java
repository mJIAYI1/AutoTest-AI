package com.autotestai.ai;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

@Component
public class AiPromptSanitizer {

    static final int MAX_BODY_CHARS = 12_000;
    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "token", "accesstoken", "refreshtoken",
            "secret", "apikey", "authorization", "cookie", "setcookie", "credential");
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
            "(?i)(password|passwd|pwd|access[_-]?token|refresh[_-]?token|token|secret|api[_-]?key|authorization|cookie)"
                    + "(\\s*[:=]\\s*)([^\\s,;&]+)");

    private final ObjectMapper objectMapper;

    public AiPromptSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, List<String>> sanitizeHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> sanitized = new LinkedHashMap<>();
        headers.forEach((name, values) -> sanitized.put(
                name,
                isSensitive(name) ? List.of(REDACTED) : List.copyOf(values)));
        return sanitized;
    }

    public Map<String, String> sanitizeStringHeaders(Map<String, String> headers) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        headers.forEach((name, value) -> sanitized.put(
                name,
                isSensitive(name) ? REDACTED : sanitizeInline(value)));
        return sanitized;
    }

    public String sanitizeBody(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        String sanitized;
        try {
            JsonNode node = objectMapper.readTree(body);
            redactJson(node);
            sanitized = objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            sanitized = BEARER_TOKEN.matcher(body).replaceAll("Bearer " + REDACTED);
            sanitized = KEY_VALUE_SECRET.matcher(sanitized).replaceAll("$1$2" + REDACTED);
        }
        return truncate(sanitized);
    }

    public String sanitizeText(String value) {
        return sanitizeInline(value);
    }

    private static String sanitizeInline(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = BEARER_TOKEN.matcher(value).replaceAll("Bearer " + REDACTED);
        return truncate(KEY_VALUE_SECRET.matcher(sanitized).replaceAll("$1$2" + REDACTED));
    }

    private void redactJson(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitive(field.getKey())) {
                    objectNode.put(field.getKey(), REDACTED);
                } else {
                    redactJson(field.getValue());
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::redactJson);
        }
    }

    private static boolean isSensitive(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEYS.contains(normalized)
                || normalized.endsWith("token")
                || normalized.endsWith("secret")
                || normalized.endsWith("password");
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_BODY_CHARS) {
            return value;
        }
        return value.substring(0, MAX_BODY_CHARS) + "...[TRUNCATED]";
    }
}
