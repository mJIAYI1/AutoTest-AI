package com.autotestai.demo;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class DemoApiExceptionHandler {

    @ExceptionHandler(IntentionalDemoBugException.class)
    ResponseEntity<Map<String, Object>> intentionalBug(IntentionalDemoBugException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", "INTENTIONAL_DEMO_BUG",
                "message", exception.getMessage(),
                "intentionalBug", true));
    }

    @ExceptionHandler(DemoConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(DemoConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "DEMO_CONFLICT",
                "message", exception.getMessage()));
    }
}

class IntentionalDemoBugException extends RuntimeException {

    IntentionalDemoBugException(String message) {
        super(message);
    }
}

class DemoConflictException extends RuntimeException {

    DemoConflictException(String message) {
        super(message);
    }
}
