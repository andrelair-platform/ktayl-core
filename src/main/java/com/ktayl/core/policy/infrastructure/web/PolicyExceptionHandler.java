package com.ktayl.core.policy.infrastructure.web;

import com.ktayl.core.policy.application.BindScreeningHoldException;
import com.ktayl.core.policy.domain.IllegalPolicyTransitionException;
import com.ktayl.core.policy.domain.InvalidPolicyException;
import com.ktayl.core.policy.domain.PolicyAlreadyExistsException;
import com.ktayl.core.policy.domain.PolicyNotFoundException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * Sanctions/AML HOLD at bind (RC-02 S2) → 409: the bind is blocked pending a human compliance decision.
     * The body carries the gate + the alert id (null on a fail-closed engine outage) so the caller can link
     * to the review. Uses a {@link HashMap} because {@code alertId} may be null and {@code Map.of} rejects it.
     */
    @ExceptionHandler(BindScreeningHoldException.class)
    public ResponseEntity<Map<String, Object>> screeningHold(BindScreeningHoldException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", 409);
        body.put("gate", e.gate());
        body.put("alertId", e.alertId());
        body.put("detail", "held pending sanctions review");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidPolicyException.class)
    public ResponseEntity<Map<String, Object>> invalid(InvalidPolicyException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("status", 422, "detail", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "detail", e.getMessage()));
    }

    /**
     * Safety net: a DB constraint violation (e.g. a value longer than its column) is a BAD INPUT → 400,
     * never a 500. The domain validates lengths up front (so this rarely fires), but this guarantees the
     * "4xx never 5xx on bad input" contract for anything the domain doesn't pre-check (QA gate, 2026-11-15).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> dataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "detail", "request violates a data constraint"));
    }
}
