package com.autotestai.execution;

import java.util.ArrayList;
import java.util.List;

import com.autotestai.dto.execution.ExtractedValue;
import com.autotestai.dto.testcase.ExtractionRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import org.springframework.stereotype.Component;

@Component
public class ResponseVariableExtractor {

    private final ObjectMapper objectMapper;

    public ResponseVariableExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ExtractedValue> extract(List<ExtractionRule> rules, String responseBody) {
        if (rules.isEmpty()) {
            return List.of();
        }
        DocumentContext json;
        try {
            json = JsonPath.parse(responseBody);
        } catch (RuntimeException exception) {
            throw new ExecutionException("Response variables require a valid JSON response body", exception);
        }
        List<ExtractedValue> values = new ArrayList<>(rules.size());
        for (ExtractionRule rule : rules) {
            try {
                Object value = json.read(rule.jsonPath());
                values.add(new ExtractedValue(rule.name(), stringify(value), rule.jsonPath()));
            } catch (PathNotFoundException exception) {
                throw new ExecutionException(
                        "Extraction path does not exist for variable " + rule.name(), exception);
            } catch (RuntimeException exception) {
                throw new ExecutionException(
                        "Extraction path could not be evaluated for variable " + rule.name(), exception);
            }
        }
        return List.copyOf(values);
    }

    private String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ExecutionException("Extracted response variable could not be encoded", exception);
        }
    }
}
