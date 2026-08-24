package com.autotestai.config;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.execution")
public record ExecutionProperties(
        Set<String> allowedHosts,
        Duration connectTimeout,
        Duration readTimeout,
        long maxResponseBodyBytes,
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        int maxSuiteCases) {

    public ExecutionProperties {
        allowedHosts = allowedHosts == null || allowedHosts.isEmpty()
                ? Set.of("localhost", "127.0.0.1")
                : Set.copyOf(allowedHosts);
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
        maxResponseBodyBytes = maxResponseBodyBytes == 0 ? 1_048_576 : maxResponseBodyBytes;
        corePoolSize = corePoolSize == 0 ? 2 : corePoolSize;
        maxPoolSize = maxPoolSize == 0 ? 4 : maxPoolSize;
        queueCapacity = queueCapacity == 0 ? 100 : queueCapacity;
        maxSuiteCases = maxSuiteCases == 0 ? 50 : maxSuiteCases;
        if (maxResponseBodyBytes < 1 || maxResponseBodyBytes > 10_485_760) {
            throw new IllegalArgumentException("Execution response body limit must be between 1 byte and 10 MB");
        }
        if (corePoolSize < 1 || maxPoolSize < corePoolSize || queueCapacity < 0) {
            throw new IllegalArgumentException("Execution thread-pool settings are invalid");
        }
        if (maxSuiteCases < 1 || maxSuiteCases > 500) {
            throw new IllegalArgumentException("Test suite size limit must be between 1 and 500");
        }
    }
}
