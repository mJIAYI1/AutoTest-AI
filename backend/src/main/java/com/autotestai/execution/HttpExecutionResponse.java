package com.autotestai.execution;

import java.util.List;
import java.util.Map;

public record HttpExecutionResponse(
        int statusCode,
        Map<String, List<String>> headers,
        String body,
        long responseTimeMs) {
}
