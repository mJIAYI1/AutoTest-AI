package com.autotestai.openapi;

import java.util.List;

public record ParsedOpenApiDocument(
        String title,
        String version,
        List<ParsedApiDefinition> definitions,
        List<String> warnings) {
}
