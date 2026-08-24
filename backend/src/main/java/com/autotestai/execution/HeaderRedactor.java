package com.autotestai.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class HeaderRedactor {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "api-key");

    public Map<String, List<String>> redact(Map<String, List<String>> headers) {
        Map<String, List<String>> redacted = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            boolean sensitive = SENSITIVE_HEADERS.contains(normalized)
                    || normalized.contains("token")
                    || normalized.contains("secret");
            redacted.put(name, sensitive ? List.of("***") : List.copyOf(values));
        });
        return Collections.unmodifiableMap(redacted);
    }
}
