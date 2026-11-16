package com.ktayl.core.screening.infrastructure.web;

import com.ktayl.core.screening.domain.AlertNotFoundException;
import com.ktayl.core.screening.domain.InvalidDecisionException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps screening domain errors to HTTP (mirrors the policy module: a bad input → 4xx, never a 5xx). */
@RestControllerAdvice(basePackageClasses = ScreeningController.class)
public class ScreeningExceptionHandler {

    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(AlertNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", 404, "detail", e.getMessage()));
    }

    @ExceptionHandler(InvalidDecisionException.class)
    public ResponseEntity<Map<String, Object>> invalidDecision(InvalidDecisionException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("status", 422, "detail", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "detail", e.getMessage()));
    }

    /**
     * Safety net: a DB constraint violation (a value longer than its column, a bad enum) is a BAD INPUT →
     * 400, never a 500 — guarantees the "4xx never 5xx on bad input" contract (QA gate discipline).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> dataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "detail", "request violates a data constraint"));
    }
}
