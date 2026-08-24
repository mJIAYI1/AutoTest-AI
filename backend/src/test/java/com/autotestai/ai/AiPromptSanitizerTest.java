package com.autotestai.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class AiPromptSanitizerTest {

    private final AiPromptSanitizer sanitizer = new AiPromptSanitizer(new ObjectMapper());

    @Test
    void redactsSensitiveJsonFieldsHeadersAndBearerTokens() {
        String body = sanitizer.sanitizeBody("""
                {"user":"alice","password":"plain-secret","nested":{"access_token":"token-value"}}
                """);
        Map<String, String> headers = sanitizer.sanitizeStringHeaders(Map.of(
                "Authorization", "Bearer header-secret",
                "Accept", "application/json"));

        assertThat(body)
                .contains("alice", "[REDACTED]")
                .doesNotContain("plain-secret", "token-value");
        assertThat(headers.get("Authorization")).isEqualTo("[REDACTED]");
        assertThat(headers.get("Accept")).isEqualTo("application/json");
        assertThat(sanitizer.sanitizeHeaders(Map.of("Cookie", List.of("session=secret"))).get("Cookie"))
                .containsExactly("[REDACTED]");
    }

    @Test
    void truncatesLargeBodiesBeforeTheyEnterTheModelPrompt() {
        String sanitized = sanitizer.sanitizeBody("x".repeat(AiPromptSanitizer.MAX_BODY_CHARS + 50));

        assertThat(sanitized)
                .hasSize(AiPromptSanitizer.MAX_BODY_CHARS + "...[TRUNCATED]".length())
                .endsWith("...[TRUNCATED]");
    }
}
