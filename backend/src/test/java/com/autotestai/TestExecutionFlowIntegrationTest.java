package com.autotestai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
class TestExecutionFlowIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_BODY =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST =
            new ParameterizedTypeReference<>() {
            };

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("autotest_ai_execution_test")
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
    void executesSingleCasesAsynchronouslyAndPersistsPassFailAndError() throws Exception {
        String aliceToken = register("run-alice", "run-alice@example.com");
        String bobToken = register("run-bob", "run-bob@example.com");
        long projectId = createProject(aliceToken);
        long environmentId = createEnvironment(projectId, aliceToken);
        long apiId = importHealthApi(projectId, aliceToken);

        long passingCase = createCase(projectId, apiId, aliceToken, "Health passes", 200, false);
        Map<String, Object> passRun = runAndWait(
                projectId, apiId, passingCase, environmentId, aliceToken);
        assertThat(passRun).containsEntry("status", "PASS")
                .containsEntry("passedCount", 1)
                .containsEntry("failedCount", 0)
                .containsEntry("errorCount", 0);
        Map<String, Object> passResult = asMap(passRun.get("result"));
        assertThat(passResult)
                .containsEntry("status", "PASS")
                .containsEntry("requestMethod", "GET")
                .containsEntry("responseStatus", 200);
        assertThat((String) passResult.get("requestUrl")).contains("/actuator/health?trace=integration");
        assertThat(asMap(passResult.get("requestHeaders")))
                .containsEntry("X-Api-Key", List.of("***"));
        assertThat(asList(passResult.get("assertions"))).hasSize(4)
                .allSatisfy(item -> assertThat(asMap(item)).containsEntry("passed", true));
        assertThat(asList(passResult.get("extractedVariables"))).singleElement()
                .satisfies(item -> assertThat(asMap(item))
                        .containsEntry("name", "healthStatus")
                        .containsEntry("value", "UP"));

        long failingCase = createCase(projectId, apiId, aliceToken, "Health fails", 201, false);
        Map<String, Object> failRun = runAndWait(
                projectId, apiId, failingCase, environmentId, aliceToken);
        assertThat(failRun).containsEntry("status", "FAIL")
                .containsEntry("failedCount", 1);
        long failRunId = ((Number) failRun.get("id")).longValue();

        long errorCase = createCase(projectId, apiId, aliceToken, "Missing variable", 200, true);
        Map<String, Object> errorRun = runAndWait(
                projectId, apiId, errorCase, environmentId, aliceToken);
        assertThat(errorRun).containsEntry("status", "ERROR")
                .containsEntry("errorCount", 1);
        assertThat((String) errorRun.get("errorMessage")).contains("Missing runtime variable");

        ResponseEntity<List<Map<String, Object>>> reportList = restTemplate.exchange(
                "/api/projects/" + projectId + "/test-reports?limit=10",
                HttpMethod.GET,
                entity(null, aliceToken),
                JSON_LIST);
        assertThat(reportList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reportList.getBody()).hasSize(3);
        Map<String, Object> failingSummary = reportList.getBody().stream()
                .filter(item -> ((Number) item.get("id")).longValue() == failRunId)
                .findFirst()
                .orElseThrow();
        assertThat(failingSummary)
                .containsEntry("title", "Health fails")
                .containsEntry("status", "FAIL")
                .containsEntry("totalCount", 1)
                .containsEntry("executedCount", 1)
                .containsEntry("skippedCount", 0)
                .containsEntry("passRate", 0.0);
        assertThat(failingSummary.get("averageResponseTimeMs")).isInstanceOf(Number.class);

        ResponseEntity<Map<String, Object>> reportDetail = exchangeMap(
                "/api/projects/" + projectId + "/test-reports/" + failRunId,
                HttpMethod.GET,
                entity(null, aliceToken));
        assertThat(reportDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asMap(reportDetail.getBody().get("summary")))
                .containsEntry("title", "Health fails")
                .containsEntry("failedCount", 1);
        assertThat(asList(reportDetail.getBody().get("apis"))).singleElement()
                .satisfies(item -> {
                    Map<String, Object> apiReport = asMap(item);
                    assertThat(apiReport)
                            .containsEntry("method", "GET")
                            .containsEntry("path", "/actuator/health")
                            .containsEntry("failedCount", 1)
                            .containsEntry("passRate", 0.0);
                    assertThat(asList(apiReport.get("results"))).singleElement();
                });

        ResponseEntity<Map<String, Object>> hiddenReport = exchangeMap(
                "/api/projects/" + projectId + "/test-reports/" + failRunId,
                HttpMethod.GET,
                entity(null, bobToken));
        assertThat(hiddenReport.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hiddenReport.getBody()).containsEntry("code", "TEST_REPORT_NOT_FOUND");

        ResponseEntity<Map<String, Object>> dashboard = exchangeMap(
                "/api/dashboard/summary",
                HttpMethod.GET,
                entity(null, aliceToken));
        assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dashboard.getBody())
                .containsEntry("recentWindowDays", 7)
                .containsEntry("projectCount", 1)
                .containsEntry("apiCount", 1)
                .containsEntry("testCaseCount", 3)
                .containsEntry("recentRunCount", 3)
                .containsEntry("overallPassRate", 33.33);
        assertThat(asList(dashboard.getBody().get("recentFailures")))
                .hasSize(2)
                .allSatisfy(item -> {
                    Map<String, Object> failure = asMap(item);
                    assertThat(failure).containsEntry("projectName", "Execution API")
                            .containsEntry("method", "GET")
                            .containsEntry("path", "/actuator/health");
                    assertThat(failure.get("status")).isIn("FAIL", "ERROR");
                });
        List<Object> passRateTrend = asList(dashboard.getBody().get("passRateTrend"));
        assertThat(passRateTrend).hasSize(7);
        assertThat(asMap(passRateTrend.get(6)))
                .containsEntry("date", java.time.LocalDate.now().toString())
                .containsEntry("passedCount", 1)
                .containsEntry("totalCount", 3)
                .containsEntry("passRate", 33.33);
        List<Object> responseTimeTrend = asList(dashboard.getBody().get("responseTimeTrend"));
        assertThat(responseTimeTrend).hasSize(7);
        assertThat(asMap(responseTimeTrend.get(6)))
                .containsEntry("date", java.time.LocalDate.now().toString())
                .containsEntry("sampleCount", 2);
        assertThat(asMap(responseTimeTrend.get(6)).get("averageResponseTimeMs"))
                .isInstanceOf(Number.class);
        assertThat(asList(dashboard.getBody().get("topFailingApis"))).singleElement()
                .satisfies(item -> assertThat(asMap(item))
                        .containsEntry("method", "GET")
                        .containsEntry("path", "/actuator/health")
                        .containsEntry("failureCount", 2)
                        .containsEntry("executionCount", 3)
                        .containsEntry("failureRate", 66.67));

        ResponseEntity<Map<String, Object>> bobDashboard = exchangeMap(
                "/api/dashboard/summary",
                HttpMethod.GET,
                entity(null, bobToken));
        assertThat(bobDashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bobDashboard.getBody())
                .containsEntry("projectCount", 0)
                .containsEntry("apiCount", 0)
                .containsEntry("testCaseCount", 0)
                .containsEntry("recentRunCount", 0)
                .containsEntry("overallPassRate", 0.0);
        assertThat(asList(bobDashboard.getBody().get("recentFailures"))).isEmpty();
        assertThat(asList(bobDashboard.getBody().get("passRateTrend"))).hasSize(7)
                .allSatisfy(item -> assertThat(asMap(item).get("passRate")).isNull());
        assertThat(asList(bobDashboard.getBody().get("responseTimeTrend"))).hasSize(7)
                .allSatisfy(item -> assertThat(asMap(item).get("averageResponseTimeMs")).isNull());
        assertThat(asList(bobDashboard.getBody().get("topFailingApis"))).isEmpty();

        long runId = ((Number) passRun.get("id")).longValue();
        ResponseEntity<Map<String, Object>> hidden = exchangeMap(
                "/api/projects/" + projectId + "/test-runs/" + runId,
                HttpMethod.GET,
                entity(null, bobToken));
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden.getBody()).containsEntry("code", "TEST_RUN_NOT_FOUND");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_results", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM extracted_variables", Integer.class))
                .isEqualTo(2);
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
                        "name", "Execution API",
                        "baseUrl", "http://127.0.0.1:" + serverPort), token));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private long createEnvironment(long projectId, String token) {
        ResponseEntity<Map<String, Object>> response = exchangeMap(
                "/api/projects/" + projectId + "/environments",
                HttpMethod.POST,
                entity(Map.of(
                        "name", "Local integration",
                        "baseUrl", "http://127.0.0.1:" + serverPort,
                        "headers", Map.of("Accept", "application/json"),
                        "variables", Map.of("token", "integration-secret")), token));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private long importHealthApi(long projectId, String token) {
        String document = """
                openapi: 3.0.3
                info:
                  title: Health API
                  version: 1.0.0
                paths:
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
                return "health-api.yaml";
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

    private long createCase(
            long projectId,
            long apiId,
            String token,
            String name,
            int expectedStatus,
            boolean missingVariable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("type", expectedStatus == 200 ? "NORMAL" : "NEGATIVE");
        payload.put("requestHeaders", Map.of("X-Api-Key", "{{token}}"));
        payload.put("pathParameters", Map.of());
        payload.put("queryParameters", Map.of(
                "trace", missingVariable ? "{{missing}}" : "integration"));
        payload.put("requestBody", null);
        payload.put("assertions", List.of(
                Map.of("type", "STATUS_CODE", "expected", expectedStatus),
                Map.of("type", "JSON_PATH_EQUALS", "expression", "$.status", "expected", "UP"),
                Map.of("type", "RESPONSE_TIME_LT", "expected", 5000),
                Map.of("type", "BODY_CONTAINS", "expected", "UP")));
        payload.put("extractionRules", List.of(
                Map.of("name", "healthStatus", "jsonPath", "$.status")));
        payload.put("enabled", true);
        ResponseEntity<Map<String, Object>> response = exchangeMap(
                "/api/projects/" + projectId + "/apis/" + apiId + "/test-cases",
                HttpMethod.POST,
                entity(payload, token));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Map<String, Object> runAndWait(
            long projectId,
            long apiId,
            long testCaseId,
            long environmentId,
            String token) throws InterruptedException {
        ResponseEntity<Map<String, Object>> started = exchangeMap(
                "/api/projects/" + projectId + "/apis/" + apiId
                        + "/test-cases/" + testCaseId + "/runs",
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
        throw new AssertionError("Test run did not reach a terminal state");
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
