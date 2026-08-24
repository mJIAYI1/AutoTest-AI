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
class ProjectOwnershipIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_BODY =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST =
            new ParameterizedTypeReference<>() {
            };

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("autotest_ai_project_test")
            .withUsername("autotest_test")
            .withPassword("autotest_test_password");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearUsersAndProjects() {
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void projectCrudEnforcesJwtOwnership() {
        String aliceToken = register("alice", "alice@example.com");
        String bobToken = register("bob", "bob@example.com");

        Map<String, Object> createRequest = Map.of(
                "name", "RAG Knowledge Base",
                "description", "Enterprise retrieval project",
                "baseUrl", "http://localhost:8081");
        ResponseEntity<Map<String, Object>> created = exchangeMap(
                "/api/projects",
                HttpMethod.POST,
                entity(createRequest, aliceToken));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        long projectId = ((Number) created.getBody().get("id")).longValue();
        assertThat(created.getBody())
                .containsEntry("name", "RAG Knowledge Base")
                .containsEntry("baseUrl", "http://localhost:8081");
        assertThat(created.getBody().get("createdAt")).isNotNull();

        ResponseEntity<Map<String, Object>> duplicate = exchangeMap(
                "/api/projects",
                HttpMethod.POST,
                entity(createRequest, aliceToken));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).containsEntry("code", "PROJECT_NAME_TAKEN");

        ResponseEntity<List<Map<String, Object>>> aliceProjects = restTemplate.exchange(
                "/api/projects",
                HttpMethod.GET,
                entity(null, aliceToken),
                JSON_LIST);
        assertThat(aliceProjects.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aliceProjects.getBody()).hasSize(1);

        ResponseEntity<List<Map<String, Object>>> bobProjects = restTemplate.exchange(
                "/api/projects",
                HttpMethod.GET,
                entity(null, bobToken),
                JSON_LIST);
        assertThat(bobProjects.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bobProjects.getBody()).isEmpty();

        String projectPath = "/api/projects/" + projectId;
        ResponseEntity<Map<String, Object>> bobRead = exchangeMap(
                projectPath,
                HttpMethod.GET,
                entity(null, bobToken));
        assertNotFound(bobRead);

        Map<String, Object> maliciousUpdate = Map.of(
                "name", "Stolen project",
                "description", "should not be saved",
                "baseUrl", "https://attacker.example.com");
        ResponseEntity<Map<String, Object>> bobUpdate = exchangeMap(
                projectPath,
                HttpMethod.PUT,
                entity(maliciousUpdate, bobToken));
        assertNotFound(bobUpdate);

        ResponseEntity<Map<String, Object>> bobDelete = exchangeMap(
                projectPath,
                HttpMethod.DELETE,
                entity(null, bobToken));
        assertNotFound(bobDelete);

        Map<String, Object> updateRequest = Map.of(
                "name", "RAG Platform",
                "description", "Updated by owner",
                "baseUrl", "https://test.example.com/api");
        ResponseEntity<Map<String, Object>> updated = exchangeMap(
                projectPath,
                HttpMethod.PUT,
                entity(updateRequest, aliceToken));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody())
                .containsEntry("name", "RAG Platform")
                .containsEntry("description", "Updated by owner")
                .containsEntry("baseUrl", "https://test.example.com/api");

        Map<String, Object> invalidUrlRequest = Map.of(
                "name", "Invalid target",
                "baseUrl", "ftp://example.com/file");
        ResponseEntity<Map<String, Object>> invalidUrl = exchangeMap(
                "/api/projects",
                HttpMethod.POST,
                entity(invalidUrlRequest, aliceToken));
        assertThat(invalidUrl.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidUrl.getBody()).containsEntry("code", "INVALID_BASE_URL");

        ResponseEntity<Void> deleted = restTemplate.exchange(
                projectPath,
                HttpMethod.DELETE,
                entity(null, aliceToken),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map<String, Object>> afterDelete = exchangeMap(
                projectPath,
                HttpMethod.GET,
                entity(null, aliceToken));
        assertNotFound(afterDelete);
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

    private static void assertNotFound(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "PROJECT_NOT_FOUND");
    }
}
