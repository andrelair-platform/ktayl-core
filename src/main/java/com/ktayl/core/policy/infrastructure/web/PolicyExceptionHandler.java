package com.ktayl.core.policy.infrastructure.web;

import com.ktayl.core.policy.domain.IllegalPolicyTransitionException;
import com.ktayl.core.policy.domain.InvalidPolicyException;
import com.ktayl.core.policy.domain.PolicyAlreadyExistsException;
import com.ktayl.core.policy.domain.PolicyNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps policy domain errors to HTTP (mirrors the PAS: 404/409/422; a bad input → 4xx, never a 5xx). */
@RestControllerAdvice(basePackageClasses = PolicyController.class)
public class PolicyExceptionHandler {

    @ExceptionHandler(PolicyNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(PolicyNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", 404, "detail", e.getMessage()));
    }

    @ExceptionHandler(PolicyAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> conflict(PolicyAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", 409, "detail", e.getMessage()));
    }

    @ExceptionHandler(IllegalPolicyTransitionException.class)
    public ResponseEntity<Map<String, Object>> illegalTransition(IllegalPolicyTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", 409, "detail", e.getMessage()));
    }

    @ExceptionHandler(InvalidPolicyException.class)
    public ResponseEntity<Map<String, Object>> invalid(InvalidPolicyException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("status", 422, "detail", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "detail", e.getMessage()));
    }
}
