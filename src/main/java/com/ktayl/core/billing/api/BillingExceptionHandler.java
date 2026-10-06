package com.ktayl.core.billing.api;

import com.ktayl.core.billing.invoicing.PolicyNotIngestedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps billing domain errors to HTTP (4xx, never a 5xx for a known business condition). */
@RestControllerAdvice(basePackageClasses = InvoiceController.class)
public class BillingExceptionHandler {

    @ExceptionHandler(PolicyNotIngestedException.class)
    public ResponseEntity<Map<String, Object>> notIngested(PolicyNotIngestedException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", 404, "detail", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", 400, "detail", e.getMessage()));
    }
}
