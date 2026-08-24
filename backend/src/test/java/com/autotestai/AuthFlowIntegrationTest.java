package com.autotestai;

import static org.assertj.core.api.Assertions.assertThat;

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
class AuthFlowIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_BODY =
            new ParameterizedTypeReference<>() {
            };

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("autotest_ai_auth_test")
            .withUsername("autotest_test")
            .withPassword("autotest_test_password");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearUsers() {
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void registrationLoginAndProfileFlowWorksWithJwt() {
        Map<String, Object> registration = Map.of(
                "username", "alice",
                "email", "ALICE@example.com",
                "password", "StrongPass123!",
                "displayName", "Alice");

        ResponseEntity<Map<String, Object>> registered = exchange(
                "/api/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(registration));

        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registered.getBody()).isNotNull();
        String accessToken = (String) registered.getBody().get("accessToken");
        assertThat(accessToken).isNotBlank();
        assertThat(registered.getBody().get("tokenType")).isEqualTo("Bearer");
        @SuppressWarnings("unchecked")
        Map<String, Object> registeredUser = (Map<String, Object>) registered.getBody().get("user");
        assertThat(registeredUser.get("createdAt")).isNotNull();
        assertThat(registeredUser.get("updatedAt")).isNotNull();

        String storedPassword = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE username = ?",
                String.class,
                "alice");
        assertThat(storedPassword)
                .startsWith("{bcrypt}")
                .doesNotContain("StrongPass123!");

        ResponseEntity<Map<String, Object>> withoutToken = exchange(
                "/api/users/me",
                HttpMethod.GET,
                HttpEntity.EMPTY);
        assertThat(withoutToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders bearerHeaders = new HttpHeaders();
        bearerHeaders.setBearerAuth(accessToken);
        ResponseEntity<Map<String, Object>> currentUser = exchange(
                "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders));
        assertThat(currentUser.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(currentUser.getBody()).containsEntry("username", "alice");
        assertThat(currentUser.getBody()).containsEntry("email", "alice@example.com");

        Map<String, Object> update = Map.of(
                "email", "alice.new@example.com",
                "displayName", "Alice Updated");
        ResponseEntity<Map<String, Object>> updated = exchange(
                "/api/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(update, bearerHeaders));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("displayName", "Alice Updated");
        assertThat(updated.getBody()).containsEntry("email", "alice.new@example.com");

        Map<String, Object> badLogin = Map.of("username", "alice", "password", "wrong-password");
        ResponseEntity<Map<String, Object>> rejected = exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(badLogin));
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rejected.getBody()).containsEntry("code", "INVALID_CREDENTIALS");

        Map<String, Object> login = Map.of("username", "alice", "password", "StrongPass123!");
        ResponseEntity<Map<String, Object>> loggedIn = exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(login));
        assertThat(loggedIn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loggedIn.getBody().get("accessToken")).isInstanceOf(String.class);

        ResponseEntity<Map<String, Object>> duplicate = exchange(
                "/api/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(registration));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).containsEntry("code", "USERNAME_TAKEN");
    }

    private ResponseEntity<Map<String, Object>> exchange(
            String path,
            HttpMethod method,
            HttpEntity<?> request) {
        return restTemplate.exchange(path, method, request, JSON_BODY);
    }
}
