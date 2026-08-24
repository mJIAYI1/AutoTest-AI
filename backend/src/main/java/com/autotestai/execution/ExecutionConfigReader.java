package com.autotestai.execution;

import java.util.List;

import com.autotestai.dto.testcase.ExtractionRule;
import com.autotestai.dto.testcase.TestAssertion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
public class ExecutionConfigReader {

    private static final TypeReference<List<TestAssertion>> ASSERTIONS = new TypeReference<>() {
    };
    private static final TypeReference<List<ExtractionRule>> EXTRACTIONS = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ExecutionConfigReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TestAssertion> assertions(String value) {
        return read(value, ASSERTIONS, "assertions");
    }

    public List<ExtractionRule> extractions(String value) {
        return read(value, EXTRACTIONS, "extraction rules");
    }

    private <T> List<T> read(String value, TypeReference<List<T>> type, String label) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(value, type));
        } catch (JsonProcessingException exception) {
            throw new ExecutionException("Stored " + label + " are invalid JSON", exception);
        }
    }
}
