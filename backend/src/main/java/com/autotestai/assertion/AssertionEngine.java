package com.autotestai.assertion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.autotestai.dto.execution.AssertionResult;
import com.autotestai.dto.testcase.TestAssertion;
import com.autotestai.execution.HttpExecutionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import org.springframework.stereotype.Component;

@Component
public class AssertionEngine {

    private final ObjectMapper objectMapper;

    public AssertionEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<AssertionResult> evaluate(
            List<TestAssertion> assertions,
            HttpExecutionResponse response) {
        List<AssertionResult> results = new ArrayList<>(assertions.size());
        DocumentContext json = null;
        RuntimeException jsonFailure = null;
        if (assertions.stream().anyMatch(item -> item.type().name().startsWith("JSON_PATH_"))) {
            try {
                json = JsonPath.parse(response.body());
            } catch (RuntimeException exception) {
                jsonFailure = exception;
            }
        }
        for (TestAssertion assertion : assertions) {
            results.add(evaluateOne(assertion, response, json, jsonFailure));
        }
        return List.copyOf(results);
    }

    private AssertionResult evaluateOne(
            TestAssertion assertion,
            HttpExecutionResponse response,
            DocumentContext json,
            RuntimeException jsonFailure) {
        return switch (assertion.type()) {
            case STATUS_CODE -> compare(
                    assertion,
                    IntNode.valueOf(response.statusCode()),
                    response.statusCode() == assertion.expected().intValue(),
                    "HTTP status code");
            case RESPONSE_TIME_LT -> compare(
                    assertion,
                    LongNode.valueOf(response.responseTimeMs()),
                    response.responseTimeMs() < assertion.expected().longValue(),
                    "Response time");
            case BODY_CONTAINS -> compare(
                    assertion,
                    TextNode.valueOf(response.body()),
                    response.body().contains(assertion.expected().textValue()),
                    "Response body content");
            case JSON_PATH_EXISTS -> evaluateJsonExists(assertion, json, jsonFailure);
            case JSON_PATH_EQUALS -> evaluateJsonEquals(assertion, json, jsonFailure);
            case JSON_PATH_TYPE -> evaluateJsonType(assertion, json, jsonFailure);
        };
    }

    private AssertionResult evaluateJsonExists(
            TestAssertion assertion,
            DocumentContext json,
            RuntimeException jsonFailure) {
        if (jsonFailure != null) {
            return failedJson(assertion, "Response body is not valid JSON");
        }
        try {
            Object actual = json.read(assertion.expression());
            return new AssertionResult(
                    assertion.type(),
                    assertion.expression(),
                    assertion.expected(),
                    toNode(actual),
                    true,
                    "JSON path exists");
        } catch (PathNotFoundException exception) {
            return failedJson(assertion, "JSON path does not exist");
        } catch (RuntimeException exception) {
            return failedJson(assertion, "JSON path could not be evaluated");
        }
    }

    private AssertionResult evaluateJsonEquals(
            TestAssertion assertion,
            DocumentContext json,
            RuntimeException jsonFailure) {
        if (jsonFailure != null) {
            return failedJson(assertion, "Response body is not valid JSON");
        }
        try {
            JsonNode actual = toNode(json.read(assertion.expression()));
            return compare(assertion, actual, assertion.expected().equals(actual), "JSON value");
        } catch (PathNotFoundException exception) {
            return failedJson(assertion, "JSON path does not exist");
        } catch (RuntimeException exception) {
            return failedJson(assertion, "JSON path could not be evaluated");
        }
    }

    private AssertionResult evaluateJsonType(
            TestAssertion assertion,
            DocumentContext json,
            RuntimeException jsonFailure) {
        if (jsonFailure != null) {
            return failedJson(assertion, "Response body is not valid JSON");
        }
        try {
            JsonNode actual = toNode(json.read(assertion.expression()));
            String expectedType = assertion.expected().textValue().toUpperCase(Locale.ROOT);
            boolean passed = matchesType(actual, expectedType);
            return new AssertionResult(
                    assertion.type(),
                    assertion.expression(),
                    assertion.expected(),
                    TextNode.valueOf(typeName(actual)),
                    passed,
                    passed ? "JSON type matched" : "JSON type did not match");
        } catch (PathNotFoundException exception) {
            return failedJson(assertion, "JSON path does not exist");
        } catch (RuntimeException exception) {
            return failedJson(assertion, "JSON path could not be evaluated");
        }
    }

    private AssertionResult compare(
            TestAssertion assertion,
            JsonNode actual,
            boolean passed,
            String label) {
        return new AssertionResult(
                assertion.type(),
                assertion.expression(),
                assertion.expected(),
                actual,
                passed,
                label + (passed ? " matched" : " did not match"));
    }

    private AssertionResult failedJson(TestAssertion assertion, String message) {
        return new AssertionResult(
                assertion.type(),
                assertion.expression(),
                assertion.expected(),
                NullNode.getInstance(),
                false,
                message);
    }

    private JsonNode toNode(Object value) {
        return value == null ? NullNode.getInstance() : objectMapper.valueToTree(value);
    }

    private static boolean matchesType(JsonNode node, String expectedType) {
        return switch (expectedType) {
            case "STRING" -> node.isTextual();
            case "NUMBER" -> node.isNumber();
            case "INTEGER" -> node.isIntegralNumber();
            case "BOOLEAN" -> node.isBoolean();
            case "ARRAY" -> node.isArray();
            case "OBJECT" -> node.isObject();
            case "NULL" -> node.isNull();
            default -> false;
        };
    }

    private static String typeName(JsonNode node) {
        if (node.isTextual()) return "STRING";
        if (node.isIntegralNumber()) return "INTEGER";
        if (node.isNumber()) return "NUMBER";
        if (node.isBoolean()) return "BOOLEAN";
        if (node.isArray()) return "ARRAY";
        if (node.isObject()) return "OBJECT";
        if (node.isNull()) return "NULL";
        return "UNKNOWN";
    }
}
