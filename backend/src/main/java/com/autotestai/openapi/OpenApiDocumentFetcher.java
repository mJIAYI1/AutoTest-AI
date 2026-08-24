package com.autotestai.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.autotestai.config.OpenApiImportProperties;
import com.autotestai.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OpenApiDocumentFetcher {

    private final OpenApiImportProperties properties;
    private final Set<String> allowedHosts;
    private final HttpClient httpClient;

    public OpenApiDocumentFetcher(OpenApiImportProperties properties) {
        this.properties = properties;
        this.allowedHosts = properties.allowedHosts().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public String fetch(String sourceUrl) {
        URI uri = validateSourceUrl(sourceUrl);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.readTimeout())
                .header("Accept", "application/json, application/yaml, application/x-yaml, text/yaml, */*")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                closeQuietly(response.body());
                throw fetchFailed("OpenAPI URL returned HTTP " + response.statusCode());
            }
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (declaredLength > properties.maxDocumentBytes()) {
                closeQuietly(response.body());
                throw documentTooLarge();
            }
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(properties.maxDocumentBytes() + 1);
                if (bytes.length > properties.maxDocumentBytes()) {
                    throw documentTooLarge();
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw fetchFailed("OpenAPI URL request was interrupted");
        } catch (IOException exception) {
            throw fetchFailed("OpenAPI URL could not be read");
        }
    }

    private URI validateSourceUrl(String value) {
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || host == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw invalidSourceUrl();
            }
            if (!allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "OPENAPI_HOST_NOT_ALLOWED",
                        "OpenAPI URL host is not in OPENAPI_IMPORT_ALLOWED_HOSTS");
            }
            return uri;
        } catch (URISyntaxException | NullPointerException exception) {
            throw invalidSourceUrl();
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Nothing else can be recovered from a rejected response body.
        }
    }

    private static ApiException invalidSourceUrl() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_OPENAPI_URL",
                "OpenAPI URL must be an absolute HTTP or HTTPS URL without credentials or fragment");
    }

    private static ApiException documentTooLarge() {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "OPENAPI_DOCUMENT_TOO_LARGE",
                "OpenAPI document exceeds the configured size limit");
    }

    private static ApiException fetchFailed(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "OPENAPI_FETCH_FAILED", message);
    }
}
