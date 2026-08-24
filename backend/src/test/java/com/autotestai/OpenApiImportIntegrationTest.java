package com.autotestai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class OpenApiImportIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_BODY =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST =
            new ParameterizedTypeReference<>() {
            };

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("autotest_ai_openapi_test")
            .withUsername("autotest_test")
            .withPassword("autotest_test_password");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearUsersAndOwnedResources() {
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void importsJsonAndYamlByFileAndUrlWithOwnershipAndUpsertProtection() throws IOException {
        String aliceToken = register("openapi-alice", "openapi-alice@example.com");
        String bobToken = register("openapi-bob", "openapi-bob@example.com");
        long projectId = createProject(aliceToken, "Imported APIs");
        String apisPath = "/api/projects/" + projectId + "/apis";

        ResponseEntity<Map<String, Object>> importedFile = importFile(
                apisPath + "/import/file",
                "demo-api.yaml",
                yamlDocument("List users"),
                aliceToken);
        assertThat(importedFile.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(importedFile.getBody())
                .containsEntry("title", "Demo API")
                .containsEntry("version", "1.0.0")
                .containsEntry("importedCount", 2);

        ResponseEntity<List<Map<String, Object>>> firstList = restTemplate.exchange(
                apisPath,
                HttpMethod.GET,
                entity(null, aliceToken),
                JSON_LIST);
        assertThat(firstList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstList.getBody()).hasSize(2);
        Map<String, Object> getUsers = firstList.getBody().stream()
                .filter(api -> api.get("method").equals("GET"))
                .findFirst()
                .orElseThrow();
        assertThat(getUsers)
                .containsEntry("path", "/users")
                .containsEntry("operationId", "listUsers")
                .containsEntry("summary", "List users");
        assertThat(asList(getUsers.get("tags"))).containsExactly("Users");
        long apiId = ((Number) getUsers.get("id")).longValue();

        ResponseEntity<Map<String, Object>> detail = exchangeMap(
                apisPath + "/" + apiId,
                HttpMethod.GET,
                entity(null, aliceToken));
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asList(detail.getBody().get("parameters"))).singleElement()
                .satisfies(parameter -> assertThat(asMap(parameter))
                        .containsEntry("name", "limit")
                        .containsEntry("in", "query"));
        assertThat(asMap(detail.getBody().get("responseSchema"))).containsKey("200");

        ResponseEntity<Map<String, Object>> reimported = importFile(
                apisPath + "/import/file",
                "demo-api.yml",
                yamlDocument("List all users"),
                aliceToken);
        assertThat(reimported.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM apis WHERE project_id = ?",
                Integer.class,
                projectId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT summary FROM apis WHERE project_id = ? AND method = 'GET' AND path = '/users'",
                String.class,
                projectId)).isEqualTo("List all users");

        HttpServer server = startOpenApiServer();
        try {
            int port = server.getAddress().getPort();
            ResponseEntity<Map<String, Object>> importedUrl = exchangeMap(
                    apisPath + "/import/url",
                    HttpMethod.POST,
                    entity(Map.of("url", "http://127.0.0.1:" + port + "/openapi.json"), aliceToken));
            assertThat(importedUrl.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(importedUrl.getBody()).containsEntry("importedCount", 1);
        } finally {
            server.stop(0);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM apis WHERE project_id = ?",
                Integer.class,
                projectId)).isEqualTo(3);

        ResponseEntity<Map<String, Object>> disallowedHost = exchangeMap(
                apisPath + "/import/url",
                HttpMethod.POST,
                entity(Map.of("url", "https://example.com/openapi.json"), aliceToken));
        assertThat(disallowedHost.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(disallowedHost.getBody()).containsEntry("code", "OPENAPI_HOST_NOT_ALLOWED");

        ResponseEntity<Map<String, Object>> invalidExtension = importFile(
                apisPath + "/import/file",
                "demo-api.txt",
                yamlDocument("Ignored"),
                aliceToken);
        assertThat(invalidExtension.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidExtension.getBody()).containsEntry("code", "INVALID_OPENAPI_FILE");

        ResponseEntity<Map<String, Object>> hiddenList = exchangeMap(
                apisPath,
                HttpMethod.GET,
                entity(null, bobToken));
        assertProjectNotFound(hiddenList);
        ResponseEntity<Map<String, Object>> hiddenImport = exchangeMap(
                apisPath + "/import/url",
                HttpMethod.POST,
                entity(Map.of("url", "http://127.0.0.1:1/not-contacted"), bobToken));
        assertProjectNotFound(hiddenImport);
    }

    private HttpServer startOpenApiServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openapi.json", exchange -> {
            byte[] body = urlDocument().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        return server;
    }

    private ResponseEntity<Map<String, Object>> importFile(
            String path,
            String filename,
            String contents,
            String token) {
        ByteArrayResource resource = new ByteArrayResource(contents.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JSON_BODY);
    }

    private String register(String username, String email) {
        ResponseEntity<Map<String, Object>> response = exchangeMap(
                "/api/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "username", username,
                        "email", email,
                        "password", "StrongPass123!",
                        "displayName", username)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("accessToken");
    }

    private long createProject(String token, String name) {
        ResponseEntity<Map<String, Object>> response = exchangeMap(
                "/api/projects",
                HttpMethod.POST,
                entity(Map.of("name", name), token));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private ResponseEntity<Map<String, Object>> exchangeMap(
            String path,
            HttpMethod method,
            HttpEntity<?> request) {
        return restTemplate.exchange(path, method, request, JSON_BODY);
    }

    private static HttpEntity<?> entity(Object body, String token) {
        HttpHeaders headers = bearerHeaders(token);
        return body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
    }

    private static HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return (List<Object>) value;
    }

    private static void assertProjectNotFound(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "PROJECT_NOT_FOUND");
    }

    private static String yamlDocument(String getSummary) {
        return """
                openapi: 3.0.3
                info:
                  title: Demo API
                  version: 1.0.0
                paths:
                  /users:
                    get:
                      operationId: listUsers
                      summary: %s
                      tags: [Users]
                      parameters:
                        - name: limit
                          in: query
                          schema:
                            type: integer
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: array
                                items:
                                  $ref: '#/components/schemas/User'
                    post:
                      operationId: createUser
                      summary: Create a user
                      tags: [Users]
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              $ref: '#/components/schemas/User'
                      responses:
                        '201':
                          description: Created
                components:
                  schemas:
                    User:
                      type: object
                      required: [username]
                      properties:
                        username:
                          type: string
                """.formatted(getSummary);
    }

    private static String urlDocument() {
        return """
                {
                  "openapi": "3.0.3",
                  "info": {"title": "Orders API", "version": "1.0.0"},
                  "paths": {
                    "/orders/{id}": {
                      "get": {
                        "operationId": "getOrder",
                        "tags": ["Orders"],
                        "parameters": [
                          {"name": "id", "in": "path", "required": true, "schema": {"type": "string"}}
                        ],
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  }
                }
                """;
    }
}
