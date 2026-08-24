package com.autotestai.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.autotestai.config.ExecutionProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class HttpExecutionClientTest {

    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> respond(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/missing", exchange -> respond(exchange, 404, "missing"));
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/ok");
            respond(exchange, 302, "redirect");
        });
        server.createContext("/large", exchange -> respond(exchange, 200, "x".repeat(200)));
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsSuccessAndErrorResponsesWithoutThrowing() {
        HttpExecutionClient client = client(1024);
        HttpExecutionResponse ok = client.execute(request("/ok"));
        HttpExecutionResponse missing = client.execute(request("/missing"));

        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).contains("UP");
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).isEqualTo("missing");
    }

    @Test
    void doesNotFollowRedirectsAndLimitsResponseBodySize() {
        HttpExecutionClient client = client(64);

        assertThat(client.execute(request("/redirect")).statusCode()).isEqualTo(302);
        assertThatThrownBy(() -> client.execute(request("/large")))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("size limit");
    }

    private HttpExecutionClient client(long maxBytes) {
        return new HttpExecutionClient(new ExecutionProperties(
                Set.of("127.0.0.1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                maxBytes,
                1,
                1,
                10,
                50));
    }

    private PreparedRequest request(String path) {
        return new PreparedRequest(
                baseUri.resolve(path),
                HttpMethod.GET,
                Map.of("Accept", List.of("application/json")),
                null);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
