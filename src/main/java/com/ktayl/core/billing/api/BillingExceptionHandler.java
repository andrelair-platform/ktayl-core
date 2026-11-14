package com.ktayl.core.billing.api;

import com.ktayl.core.billing.invoicing.PolicyNotIngestedException;
import com.ktayl.core.billing.payment.InstallmentNotFoundException;
import com.ktayl.core.billing.payment.PaymentGatewayUnavailableException;
import com.ktayl.core.billing.payment.PaymentNotAllowedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps billing domain errors to HTTP (4xx for a known business condition, 503 for PSP availability). */
@RestControllerAdvice(basePackageClasses = InvoiceController.class)
public class BillingExceptionHandler {

    @ExceptionHandler({PolicyNotIngestedException.class, InstallmentNotFoundException.class})
    public ResponseEntity<Map<String, Object>> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", 404, "detail", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", 400, "detail", e.getMessage()));
    }

    /** Over-pay / payment on a settled installment → 409 (rejected before the PSP). */
    @ExceptionHandler(PaymentNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> notAllowed(PaymentNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("status", 409, "detail", e.getMessage()));
    }

    /** PSP not configured / transiently unreachable → 503 (retryable; nothing persisted). */
    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    public ResponseEntity<Map<String, Object>> gatewayUnavailable(PaymentGatewayUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", 503, "detail", e.getMessage()));
    }
}
