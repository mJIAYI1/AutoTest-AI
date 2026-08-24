package com.autotestai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
class TestCaseFlowIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_BODY =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST =
            new ParameterizedTypeReference<>() {
            };

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("autotest_ai_test_case_test")
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
    void managesTestCasesWithOwnershipValidationAndOptimisticLocking() {
        String aliceToken = register("case-alice", "case-alice@example.com");
        String bobToken = register("case-bob", "case-bob@example.com");
        long projectId = createProject(aliceToken);
        long apiId = importApiAndGetId(projectId, aliceToken);
        String path = "/api/projects/" + projectId + "/apis/" + apiId + "/test-cases";

        Map<String, Object> createPayload = testCasePayload("Successful login", 200, true, 0);
        ResponseEntity<Map<String, Object>> created = exchangeMap(
                path,
                HttpMethod.POST,
                entity(createPayload, aliceToken));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody())
                .containsEntry("name", "Successful login")
                .containsEntry("type", "NORMAL")
                .containsEntry("enabled", true)
                .containsEntry("version", 1);
        assertThat(asMap(created.getBody().get("requestBody")))
                .containsEntry("username", "admin");
        assertThat(asList(created.getBody().get("assertions"))).hasSize(2);
        assertThat(asList(created.getBody().get("extractionRules"))).singleElement()
                .satisfies(rule -> assertThat(asMap(rule))
                        .containsEntry("name", "token")
                        .containsEntry("jsonPath", "$.data.token"));
        long testCaseId = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<List<Map<String, Object>>> list = restTemplate.exchange(
                path,
                HttpMethod.GET,
                entity(null, aliceToken),
                JSON_LIST);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).singleElement()
                .satisfies(item -> assertThat(item).containsEntry("id", (int) testCaseId));

        ResponseEntity<Map<String, Object>> hidden = exchangeMap(
                path,
                HttpMethod.GET,
                entity(null, bobToken));
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden.getBody()).containsEntry("code", "PROJECT_NOT_FOUND");

        ResponseEntity<Map<String, Object>> duplicate = exchangeMap(
                path,
                HttpMethod.POST,
                entity(createPayload, aliceToken));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).containsEntry("code", "TEST_CASE_NAME_TAKEN");

        Map<String, Object> updatePayload = testCasePayload("Invalid credentials", 401, false, 1);
        ResponseEntity<Map<String, Object>> updated = exchangeMap(
                path + "/" + testCaseId,
                HttpMethod.PUT,
                entity(updatePayload, aliceToken));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody())
                .containsEntry("name", "Invalid credentials")
                .containsEntry("enabled", false)
                .containsEntry("version", 2);

        ResponseEntity<Map<String, Object>> stale = exchangeMap(
                path + "/" + testCaseId,
                HttpMethod.PUT,
                entity(updatePayload, aliceToken));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody()).containsEntry("code", "STALE_TEST_CASE_VERSION");

        Map<String, Object> invalidPayload = testCasePayload("Invalid status", 99, true, 0);
        ResponseEntity<Map<String, Object>> invalid = exchangeMap(
                path,
                HttpMethod.POST,
                entity(invalidPayload, aliceToken));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).containsEntry("code", "INVALID_ASSERTION");

        ResponseEntity<Void> deleted = restTemplate.exchange(
                path + "/" + testCaseId,
                HttpMethod.DELETE,
                entity(null, aliceToken),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_cases WHERE api_id = ?",
                Integer.class,
                apiId)).isZero();
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

    private long createProject(String token) {
        ResponseEntity<Map<String, Object>> response = exchangeMap(
                "/api/projects",
                HttpMethod.POST,
                entity(Map.of("name", "Authentication API"), token));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private long importApiAndGetId(long projectId, String token) {
        String document = """
                openapi: 3.0.3
                info:
                  title: Authentication API
                  version: 1.0.0
                paths:
                  /login:
                    post:
                      operationId: login
                      summary: User login
                      responses:
                        '200':
                          description: OK
                """;
        ByteArrayResource resource = new ByteArrayResource(document.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "auth-api.yaml";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map<String, Object>> imported = exchangeMap(
                "/api/projects/" + projectId + "/apis/import/file",
                HttpMethod.POST,
                new HttpEntity<>(body, headers));
        assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<List<Map<String, Object>>> apis = restTemplate.exchange(
                "/api/projects/" + projectId + "/apis",
                HttpMethod.GET,
                entity(null, token),
                JSON_LIST);
        return ((Number) apis.getBody().get(0).get("id")).longValue();
    }

    private static Map<String, Object> testCasePayload(
            String name,
            int expectedStatus,
            boolean enabled,
            int version) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("description", "Authentication behavior");
        payload.put("type", expectedStatus == 200 ? "NORMAL" : "NEGATIVE");
        payload.put("requestHeaders", Map.of("Content-Type", "application/json"));
        payload.put("pathParameters", Map.of());
        payload.put("queryParameters", Map.of("trace", "{{trace_id}}"));
        payload.put("requestBody", Map.of("username", "admin", "password", "secret"));
        payload.put("assertions", List.of(
                Map.of("type", "STATUS_CODE", "expected", expectedStatus),
                Map.of("type", "JSON_PATH_EXISTS", "expression", "$.data.token")));
        payload.put("extractionRules", List.of(Map.of("name", "token", "jsonPath", "$.data.token")));
        payload.put("enabled", enabled);
        if (version > 0) {
            payload.put("version", version);
        }
        return payload;
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
}
