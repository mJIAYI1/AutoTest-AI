package com.autotestai.dto.openapi;

import java.util.List;

public record OpenApiImportResponse(
        long projectId,
        String title,
        String version,
        int importedCount,
        List<String> warnings) {
}
