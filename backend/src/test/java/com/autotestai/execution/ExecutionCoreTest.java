package com.autotestai.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.autotestai.assertion.AssertionEngine;
import com.autotestai.config.ExecutionProperties;
import com.autotestai.dto.execution.AssertionResult;
import com.autotestai.dto.execution.ExtractedValue;
import com.autotestai.dto.testcase.AssertionType;
import com.autotestai.dto.testcase.ExtractionRule;
import com.autotestai.dto.testcase.TestAssertion;
import com.autotestai.entity.ApiEntity;
import com.autotestai.entity.EnvironmentEntity;
import com.autotestai.entity.ProjectEntity;
import com.autotestai.entity.TestCaseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.junit.jupiter.api.Test;

class ExecutionCoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemplateResolver templateResolver = new TemplateResolver();

    @Test
    void resolvesTemplatesStrictly() {
        assertThat(templateResolver.resolve(
                "Bearer {{token}} / {{user_id}}",
                Map.of("token", "abc", "user_id", "42")))
                .isEqualTo("Bearer abc / 42");
        assertThatThrownBy(() -> templateResolver.resolve("{{missing}}", Map.of()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void preparesUrlHeadersBodyAndEnvironmentVariables() {
        ExecutionProperties properties = properties(Set.of("localhost"), 1_048_576);
        RequestPreparer preparer = new RequestPreparer(
                objectMapper,
                templateResolver,
                new TargetUrlPolicy(properties));

        ProjectEntity project = new ProjectEntity();
        project.setBaseUrl("http://blocked.example");
        ApiEntity api = new ApiEntity();
        api.setMethod("get");
        api.setPath("/orders/{id}");
        TestCaseEntity testCase = new TestCaseEntity();
        testCase.setPathParametersJson("{\"id\":\"{{order_id}}\"}");
        testCase.setQueryParametersJson("{\"trace\":\"{{order_id}}\"}");
        testCase.setRequestHeadersJson("{\"Authorization\":\"Bearer {{token}}\"}");
        testCase.setRequestBodyJson("{\"id\":\"{{order_id}}\"}");
        EnvironmentEntity environment = new EnvironmentEntity();
        environment.setBaseUrl("http://localhost:8081");
        environment.setHeadersJson("{\"Accept\":\"application/json\"}");
        environment.setVariablesJson("{\"order_id\":\"environment-value\",\"token\":\"secret\"}");

        PreparedRequest request = preparer.prepare(
                new TestExecutionSnapshot(project, api, testCase, environment),
                Map.of("order_id", "A B"));

        assertThat(request.uri().toString())
                .isEqualTo("http://localhost:8081/orders/A%20B?trace=A%20B");
        assertThat(request.headers())
                .containsEntry("Accept", List.of("application/json"))
                .containsEntry("Authorization", List.of("Bearer secret"));
        assertThat(request.body()).isEqualTo("{\"id\":\"A B\"}");
    }

    @Test
    void blocksTargetsOutsideTheAllowlist() {
        TargetUrlPolicy policy = new TargetUrlPolicy(properties(Set.of("localhost"), 1024));
        assertThatThrownBy(() -> policy.validate(java.net.URI.create("https://example.com/data")))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void evaluatesAllAssertionFamiliesAndReportsFailures() {
        AssertionEngine engine = new AssertionEngine(objectMapper);
        HttpExecutionResponse response = new HttpExecutionResponse(
                200,
                Map.of(),
                "{\"data\":{\"id\":7,\"name\":\"admin\"},\"message\":\"success\"}",
                45);
        List<TestAssertion> assertions = List.of(
                new TestAssertion(AssertionType.STATUS_CODE, null, IntNode.valueOf(200)),
                new TestAssertion(AssertionType.JSON_PATH_EXISTS, "$.data.id", null),
                new TestAssertion(AssertionType.JSON_PATH_EQUALS, "$.data.name", TextNode.valueOf("admin")),
                new TestAssertion(AssertionType.JSON_PATH_TYPE, "$.data.id", TextNode.valueOf("INTEGER")),
                new TestAssertion(AssertionType.RESPONSE_TIME_LT, null, IntNode.valueOf(100)),
                new TestAssertion(AssertionType.BODY_CONTAINS, null, TextNode.valueOf("success")),
                new TestAssertion(AssertionType.STATUS_CODE, null, IntNode.valueOf(201)));

        List<AssertionResult> results = engine.evaluate(assertions, response);

        assertThat(results).hasSize(7);
        assertThat(results.subList(0, 6)).allMatch(AssertionResult::passed);
        assertThat(results.get(6).passed()).isFalse();
        assertThat(results.get(6).actual().intValue()).isEqualTo(200);
    }

    @Test
    void extractsPrimitiveAndObjectResponseVariables() {
        ResponseVariableExtractor extractor = new ResponseVariableExtractor(objectMapper);
        List<ExtractedValue> values = extractor.extract(
                List.of(
                        new ExtractionRule("token", "$.data.token"),
                        new ExtractionRule("profile", "$.data.profile")),
                "{\"data\":{\"token\":\"abc\",\"profile\":{\"id\":1}}}");

        assertThat(values)
                .extracting(ExtractedValue::value)
                .containsExactly("abc", "{\"id\":1}");
    }

    private static ExecutionProperties properties(Set<String> hosts, long maxBytes) {
        return new ExecutionProperties(
                hosts,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                maxBytes,
                1,
                1,
                10,
                50);
    }
}
