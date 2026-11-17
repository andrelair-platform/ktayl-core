package com.ktayl.core.submission.infrastructure.web;

import com.ktayl.core.submission.domain.IllegalSubmissionTransitionException;
import com.ktayl.core.submission.domain.InvalidSubmissionException;
import com.ktayl.core.submission.domain.SubmissionNotFoundException;
import com.ktayl.core.submission.domain.SubmissionScreeningHoldException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the submission domain exceptions to HTTP, scoped to this module's controller package (mirrors the
 * policy/billing handler body shape: {@code {"status":…,"detail":…}}). This is where the domain's typed
 * failures become status codes — the domain itself carries no HTTP concept (ADR-013). A bad input is a 4xx,
 * never a 5xx.
 */
@RestControllerAdvice(basePackageClasses = SubmissionController.class)
class SubmissionExceptionHandler {

    @ExceptionHandler(SubmissionNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(SubmissionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", 404, "detail", e.getMessage()));
    }

    @ExceptionHandler(IllegalSubmissionTransitionException.class)
    ResponseEntity<Map<String, Object>> illegalTransition(IllegalSubmissionTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", 409, "detail", e.getMessage()));
    }

    /**
     * Routing blocked by a sanctions/AML HOLD (RC-02) → 409: the submission cannot be routed to underwriting
     * until a human compliance decision clears the hold. The body carries the alert id (may be null on a
     * fail-closed engine outage) so the caller can link to the screening alert. Uses a {@link HashMap}
     * because {@code alertId} may be null and {@code Map.of} rejects a null value.
     */
    @ExceptionHandler(SubmissionScreeningHoldException.class)
    ResponseEntity<Map<String, Object>> screeningHold(SubmissionScreeningHoldException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", 409);
        body.put("alertId", e.alertId());
        body.put("detail", "routing blocked pending sanctions review");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidSubmissionException.class)
    ResponseEntity<Map<String, Object>> invalid(InvalidSubmissionException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("status", 422, "detail", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "detail", e.getMessage()));
    }

    /**
     * Safety net: a DB constraint violation (e.g. a value longer than its column) is a BAD INPUT → 400,
     * never a 500 — guarantees the "4xx never 5xx on bad input" contract for anything the domain doesn't
     * pre-check.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> dataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "detail", "request violates a data constraint"));
    }
}
