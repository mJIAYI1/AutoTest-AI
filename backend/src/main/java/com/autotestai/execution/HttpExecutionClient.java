package com.autotestai.execution;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.autotestai.config.ExecutionProperties;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpExecutionClient {

    private static final Set<String> FORBIDDEN_HEADERS = Set.of(
            "host",
            "content-length",
            "transfer-encoding",
            "connection",
            "upgrade",
            "proxy-authorization");

    private final RestClient restClient;
    private final int maxResponseBodyBytes;

    public HttpExecutionClient(ExecutionProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.maxResponseBodyBytes = Math.toIntExact(properties.maxResponseBodyBytes());
    }

    public HttpExecutionResponse execute(PreparedRequest request) {
        long started = System.nanoTime();
        try {
            RestClient.RequestBodySpec requestSpec = restClient.method(request.method())
                    .uri(request.uri())
                    .headers(headers -> applyHeaders(headers, request.headers()));
            if (request.body() != null) {
                requestSpec.body(request.body());
            }
            return requestSpec.exchange((sentRequest, response) -> {
                long responseTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                String body = readBody(response.getBody(), response.getHeaders().getContentType());
                return new HttpExecutionResponse(
                        response.getStatusCode().value(),
                        immutableHeaders(response.getHeaders()),
                        body,
                        responseTimeMs);
            });
        } catch (ExecutionException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ExecutionException("HTTP request failed: " + safeMessage(exception), exception);
        }
    }

    private void applyHeaders(HttpHeaders target, Map<String, List<String>> source) {
        source.forEach((name, values) -> {
            if (FORBIDDEN_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                throw new ExecutionException("Request header is managed by the HTTP client and cannot be overridden: " + name);
            }
            target.put(name, List.copyOf(values));
        });
    }

    private String readBody(InputStream input, MediaType contentType) throws IOException {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxResponseBodyBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxResponseBodyBytes) {
                throw new ExecutionException("Response body exceeds the configured size limit");
            }
            output.write(buffer, 0, read);
        }
        Charset charset = contentType != null && contentType.getCharset() != null
                ? contentType.getCharset()
                : StandardCharsets.UTF_8;
        return output.toString(charset);
    }

    private static Map<String, List<String>> immutableHeaders(HttpHeaders headers) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        headers.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
