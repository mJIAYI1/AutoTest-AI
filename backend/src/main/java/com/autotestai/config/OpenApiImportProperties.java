package com.autotestai.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openapi.import")
public record OpenApiImportProperties(
        List<String> allowedHosts,
        Integer maxDocumentBytes,
        Duration connectTimeout,
        Duration readTimeout) {

    public OpenApiImportProperties {
        allowedHosts = allowedHosts == null
                ? List.of("localhost", "127.0.0.1")
                : List.copyOf(allowedHosts);
        maxDocumentBytes = maxDocumentBytes == null ? 5 * 1024 * 1024 : maxDocumentBytes;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
        if (maxDocumentBytes <= 0) {
            throw new IllegalArgumentException("OpenAPI maximum document size must be positive");
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("OpenAPI connect timeout must be positive");
        }
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("OpenAPI read timeout must be positive");
        }
    }
}
