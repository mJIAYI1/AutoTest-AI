package com.autotestai.execution;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;

public record PreparedRequest(
        URI uri,
        HttpMethod method,
        Map<String, List<String>> headers,
        String body) {
}
