package com.autotestai;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EnvironmentOwnershipIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_BODY =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST =
            new ParameterizedTypeReference<>() {
            };

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("autotest_ai_environment_test")
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
    void environmentCrudStoresJsonConfigAndEnforcesProjectOwnership() {
        String aliceToken = register("env-alice", "env-alice@example.com");
        String bobToken = register("env-bob", "env-bob@example.com");
        long projectId = createProject(aliceToken, "Payment APIs");
        String environmentsPath = "/api/projects/" + projectId + "/environments";

        Map<String, Object> createRequest = Map.of(
                "name", "Development",
                "baseUrl", "http://localhost:8081",
                "headers", Map.of(
                        "Content-Type", "application/json",
                        "X-Tenant", "demo"),
                "variables", Map.of(
                        "token", "dev-token",
                        "user_id", "1001"));
        ResponseEntity<Map<String, Object>> created = exchangeMap(
                environmentsPath,
                HttpMethod.POST,
                entity(createRequest, aliceToken));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        long environmentId = ((Number) created.getBody().get("id")).longValue();
        assertThat(((Number) created.getBody().get("projectId")).longValue()).isEqualTo(projectId);
        assertThat(created.getBody())
                .containsEntry("name", "Development")
                .containsEntry("baseUrl", "http://localhost:8081");
        assertThat(asMap(created.getBody().get("headers")))
                .containsEntry("Content-Type", "application/json")
                .containsEntry("X-Tenant", "demo");
        assertThat(asMap(created.getBody().get("variables")))
                .containsEntry("token", "dev-token")
                .containsEntry("user_id", "1001");

        ResponseEntity<Map<String, Object>> duplicate = exchangeMap(
                environmentsPath,
                HttpMethod.POST,
                entity(createRequest, aliceToken));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).containsEntry("code", "ENVIRONMENT_NAME_TAKEN");

        ResponseEntity<List<Map<String, Object>>> aliceEnvironments = restTemplate.exchange(
                environmentsPath,
                HttpMethod.GET,
                entity(null, aliceToken),
                JSON_LIST);
        assertThat(aliceEnvironments.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aliceEnvironments.getBody()).hasSize(1);

        String environmentPath = environmentsPath + "/" + environmentId;
        ResponseEntity<Map<String, Object>> detail = exchangeMap(
                environmentPath,
                HttpMethod.GET,
                entity(null, aliceToken));
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).containsEntry("name", "Development");

        assertProjectNotFound(exchangeMap(
                environmentsPath,
                HttpMethod.GET,
                entity(null, bobToken)));
        assertProjectNotFound(exchangeMap(
                environmentPath,
                HttpMethod.GET,
                entity(null, bobToken)));
        assertProjectNotFound(exchangeMap(
                environmentPath,
                HttpMethod.PUT,
                entity(createRequest, bobToken)));
        assertProjectNotFound(exchangeMap(
                environmentPath,
                HttpMethod.DELETE,
                entity(null, bobToken)));

        Map<String, Object> updateRequest = Map.of(
                "name", "Testing",
                "baseUrl", "https://test.example.com/api",
                "headers", Map.of("Accept", "application/json"),
                "variables", Map.of("token", "test-token", "order_id", "A-100"));
        ResponseEntity<Map<String, Object>> updated = exchangeMap(
                environmentPath,
                HttpMethod.PUT,
                entity(updateRequest, aliceToken));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody())
                .containsEntry("name", "Testing")
                .containsEntry("baseUrl", "https://test.example.com/api");
        assertThat(asMap(updated.getBody().get("headers")))
                .containsExactlyEntriesOf(Map.of("Accept", "application/json"));
        assertThat(asMap(updated.getBody().get("variables")))
                .containsEntry("order_id", "A-100");

        Map<String, Object> invalidUrlRequest = Map.of(
                "name", "Production",
                "baseUrl", "file:///etc/passwd");
        ResponseEntity<Map<String, Object>> invalidUrl = exchangeMap(
                environmentsPath,
                HttpMethod.POST,
                entity(invalidUrlRequest, aliceToken));
        assertThat(invalidUrl.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidUrl.getBody()).containsEntry("code", "INVALID_BASE_URL");

        Map<String, Object> invalidHeaderRequest = Map.of(
                "name", "Production",
                "baseUrl", "https://api.example.com",
                "headers", Map.of("X-Unsafe", "allowed\r\nInjected: value"));
        ResponseEntity<Map<String, Object>> invalidHeader = exchangeMap(
                environmentsPath,
                HttpMethod.POST,
                entity(invalidHeaderRequest, aliceToken));
        assertThat(invalidHeader.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidHeader.getBody()).containsEntry("code", "INVALID_ENVIRONMENT_CONFIG");

        ResponseEntity<Void> deleted = restTemplate.exchange(
                environmentPath,
                HttpMethod.DELETE,
                entity(null, aliceToken),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map<String, Object>> afterDelete = exchangeMap(
                environmentPath,
                HttpMethod.GET,
                entity(null, aliceToken));
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(afterDelete.getBody()).containsEntry("code", "ENVIRONMENT_NOT_FOUND");
    }

    private String register(String username, String email) {
        Map<String, Object> request = Map.of(
                "username", username,
                "email", email,
                "password", "StrongPass123!",
                "displayName", username);
        ResponseEntity<Map<String, Object>> response = exchangeMap(
                "/api/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(request));
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
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static void assertProjectNotFound(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "PROJECT_NOT_FOUND");
    }
}
