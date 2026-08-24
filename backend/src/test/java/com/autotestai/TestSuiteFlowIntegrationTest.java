package com.autotestai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
class TestSuiteFlowIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_BODY =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST =
            new ParameterizedTypeReference<>() {
            };

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("autotest_ai_suite_test")
            .withUsername("autotest_test")
            .withPassword("autotest_test_password");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int serverPort;

    @BeforeEach
    void clearUsersAndRuns() {
        jdbcTemplate.update("DELETE FROM test_runs");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void managesAndExecutesOrderedSuitesWithRuntimeVariablesAndStopOnFailure() throws Exception {
        String aliceToken = register("suite-alice", "suite-alice@example.com");
        String bobToken = register("suite-bob", "suite-bob@example.com");
        long projectId = createProject(aliceToken);
        long environmentId = createEnvironment(projectId, aliceToken);
        Map<String, Long> apiIds = importWorkflowApis(projectId, aliceToken);

        long producerCase = createCase(
                projectId,
                apiIds.get("/"),
                aliceToken,
                "Read platform status",
                200,
                Map.of(),
                List.of(Map.of("name", "flowStatus", "jsonPath", "$.status")));
        long consumerCase = createCase(
                projectId,
                apiIds.get("/actuator/health"),
                aliceToken,
                "Use extracted status",
                200,
                Map.of("from", "{{flowStatus}}"),
                List.of());
        long failingCase = createCase(
                projectId,
                apiIds.get("/actuator/health"),
                aliceToken,
                "Fail before next step",
                201,
                Map.of(),
                List.of());

        Map<String, Object> created = createSuite(
                projectId,
                aliceToken,
                "Platform workflow",
                false,
                List.of(producerCase, consumerCase));
        long suiteId = ((Number) created.get("id")).longValue();
        assertThat(asList(created.get("cases")))
                .extracting(item -> ((Number) asMap(item).get("sortOrder")).intValue())
                .containsExactly(1, 2);

        ResponseEntity<List<Map<String, Object>>> candidates = exchangeList(
                "/api/projects/" + projectId + "/test-suites/candidates",
                HttpMethod.GET,
                entity(null, aliceToken));
        assertThat(candidates.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(candidates.getBody())
                .extracting(item -> ((Number) item.get("testCaseId")).longValue())
                .contains(producerCase, consumerCase, failingCase);

        Map<String, Object> updatePayload = new LinkedHashMap<>();
        updatePayload.put("name", "Platform workflow updated");
        updatePayload.put("description", "Producer then consumer");
        updatePayload.put("stopOnFailure", false);
        updatePayload.put("cases", List.of(
                Map.of("testCaseId", producerCase, "enabled", true),
                Map.of("testCaseId", consumerCase, "enabled", true)));
        updatePayload.put("version", created.get("version"));
        ResponseEntity<Map<String, Object>> updated = exchangeMap(
                "/api/projects/" + projectId + "/test-suites/" + suiteId,
                HttpMethod.PUT,
                entity(updatePayload, aliceToken));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("name", "Platform workflow updated")
                .containsEntry("version", 2);

        Map<String, Object> passRun = runSuiteAndWait(
                projectId, suiteId, environmentId, aliceToken);
        assertThat(passRun).containsEntry("runType", "SUITE")
                .containsEntry("status", "PASS")
                .containsEntry("totalCount", 2)
                .containsEntry("passedCount", 2);
        assertThat(((Number) passRun.get("testSuiteId")).longValue()).isEqualTo(suiteId);
        List<Object> passResults = asList(passRun.get("results"));
        assertThat(passResults).hasSize(2);
        assertThat(passResults)
                .extracting(item -> ((Number) asMap(item).get("sequenceNumber")).intValue())
                .containsExactly(1, 2);
        assertThat((String) asMap(passResults.get(1)).get("requestUrl"))
                .contains("from=UP");
        assertThat(asList(asMap(passResults.get(0)).get("extractedVariables")))
                .singleElement()
                .satisfies(item -> assertThat(asMap(item))
                        .containsEntry("name", "flowStatus")
                        .containsEntry("value", "UP"));

        Map<String, Object> stoppedSuite = createSuite(
                projectId,
                aliceToken,
                "Stop workflow",
                true,
                List.of(failingCase, consumerCase));
        long stoppedSuiteId = ((Number) stoppedSuite.get("id")).longValue();
        Map<String, Object> stoppedRun = runSuiteAndWait(
                projectId, stoppedSuiteId, environmentId, aliceToken);
        assertThat(stoppedRun).containsEntry("status", "FAIL")
                .containsEntry("totalCount", 2)
                .containsEntry("passedCount", 0)
                .containsEntry("failedCount", 1)
                .containsEntry("errorCount", 0);
        assertThat((String) stoppedRun.get("errorMessage")).contains("STOP_ON_FAILURE");
        assertThat(asList(stoppedRun.get("results"))).hasSize(1);

        ResponseEntity<Map<String, Object>> hidden = exchangeMap(
                "/api/projects/" + projectId + "/test-suites/" + suiteId,
                HttpMethod.GET,
                entity(null, bobToken));
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden.getBody()).containsEntry("code", "PROJECT_NOT_FOUND");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_runs", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_results", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT value_text FROM extracted_variables", String.class))
                .isEqualTo("UP");
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
                entity(Map.of(
                        "name", "Suite workflow API",
                        "baseUrl", "http://127.0.0.1:" + serverPort), token));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private long createEnvironment(long projectId, String token) {
        ResponseEntity<Map<String, Object>> response = exchangeMap(
                "/api/projects/" + projectId + "/environments",
                HttpMethod.POST,
                entity(Map.of(
                        "name", "Suite local",
                        "baseUrl", "http://127.0.0.1:" + serverPort,
                        "headers", Map.of("Accept", "application/json"),
                        "variables", Map.of("flowStatus", "environment-value")), token));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Map<String, Long> importWorkflowApis(long projectId, String token) {
        String document = """
                openapi: 3.0.3
                info:
                  title: Workflow API
                  version: 1.0.0
                paths:
                  /:
                    get:
                      operationId: platformStatus
                      responses:
                        '200':
                          description: OK
                  /actuator/health:
                    get:
                      operationId: health
                      responses:
                        '200':
                          description: OK
                """;
        ByteArrayResource resource = new ByteArrayResource(document.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "workflow-api.yaml";
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
        ResponseEntity<List<Map<String, Object>>> apis = exchangeList(
                "/api/projects/" + projectId + "/apis",
                HttpMethod.GET,
                entity(null, token));
        return apis.getBody().stream().collect(Collectors.toMap(
                item -> (String) item.get("path"),
                item -> ((Number) item.get("id")).longValue(),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private long createCase(
            long projectId,
            long apiId,
            String token,
            String name,
            int expectedStatus,
            Map<String, String> queryParameters,
            List<Map<String, Object>> extractionRules) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("type", expectedStatus == 200 ? "NORMAL" : "NEGATIVE");
        payload.put("requestHeaders", Map.of());
        payload.put("pathParameters", Map.of());
        payload.put("queryParameters", queryParameters);
        payload.put("requestBody", null);
        payload.put("assertions", List.of(
                Map.of("type", "STATUS_CODE", "expected", expectedStatus),
                Map.of("type", "JSON_PATH_EQUALS", "expression", "$.status", "expected", "UP")));
        payload.put("extractionRules", extractionRules);
        payload.put("enabled", true);
        ResponseEntity<Map<String, Object>> response = exchangeMap(
                "/api/projects/" + projectId + "/apis/" + apiId + "/test-cases",
                HttpMethod.POST,
                entity(payload, token));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Map<String, Object> createSuite(
            long projectId,
            String token,
            String name,
            boolean stopOnFailure,
            List<Long> caseIds) {
        List<Map<String, Object>> cases = caseIds.stream()
                .map(caseId -> Map.<String, Object>of(
                        "testCaseId", caseId,
                        "enabled", true))
                .toList();
        ResponseEntity<Map<String, Object>> response = exchangeMap(
                "/api/projects/" + projectId + "/test-suites",
                HttpMethod.POST,
                entity(Map.of(
                        "name", name,
                        "description", "integration suite",
                        "stopOnFailure", stopOnFailure,
                        "cases", cases), token));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> runSuiteAndWait(
            long projectId,
            long suiteId,
            long environmentId,
            String token) throws InterruptedException {
        ResponseEntity<Map<String, Object>> started = exchangeMap(
                "/api/projects/" + projectId + "/test-suites/" + suiteId + "/runs",
                HttpMethod.POST,
                entity(Map.of("environmentId", environmentId), token));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long runId = ((Number) started.getBody().get("id")).longValue();
        String path = "/api/projects/" + projectId + "/test-runs/" + runId;
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            ResponseEntity<Map<String, Object>> response = exchangeMap(
                    path, HttpMethod.GET, entity(null, token));
            String status = (String) response.getBody().get("status");
            if (List.of("PASS", "FAIL", "ERROR").contains(status)) {
                return response.getBody();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Test suite run did not reach a terminal state");
    }

    private ResponseEntity<Map<String, Object>> exchangeMap(
            String path,
            HttpMethod method,
            HttpEntity<?> request) {
        return restTemplate.exchange(path, method, request, JSON_BODY);
    }

    private ResponseEntity<List<Map<String, Object>>> exchangeList(
            String path,
            HttpMethod method,
            HttpEntity<?> request) {
        return restTemplate.exchange(path, method, request, JSON_LIST);
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
