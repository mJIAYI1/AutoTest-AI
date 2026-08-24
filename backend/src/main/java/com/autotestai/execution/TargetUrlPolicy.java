package com.autotestai.execution;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.autotestai.config.ExecutionProperties;

import org.springframework.stereotype.Component;

@Component
public class TargetUrlPolicy {

    private final Set<String> allowedHosts;

    public TargetUrlPolicy(ExecutionProperties properties) {
        this.allowedHosts = properties.allowedHosts().stream()
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public void validate(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || host == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new ExecutionException("Execution target must be an absolute HTTP or HTTPS URL without credentials or fragment");
        }
        if (!allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new ExecutionException("Execution target host is not allowed: " + host);
        }
    }
}
