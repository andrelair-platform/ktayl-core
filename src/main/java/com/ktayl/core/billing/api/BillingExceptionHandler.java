package com.ktayl.core.billing.api;

import com.ktayl.core.billing.invoicing.PolicyNotIngestedException;
import com.ktayl.core.billing.payment.InstallmentNotFoundException;
import com.ktayl.core.billing.payment.PaymentGatewayUnavailableException;
import com.ktayl.core.billing.payment.PaymentNotAllowedException;
import com.ktayl.core.billing.payment.PaymentScreeningHoldException;
import java.util.HashMap;
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

    /**
     * Sanctions/AML HOLD at payment (RC-02 S4) → 409: no money moves pending a human compliance decision.
     * The body carries the gate + the alert id (null on a fail-closed engine outage). Uses a {@link HashMap}
     * because {@code alertId} may be null and {@code Map.of} rejects a null value.
     */
    @ExceptionHandler(PaymentScreeningHoldException.class)
    public ResponseEntity<Map<String, Object>> screeningHold(PaymentScreeningHoldException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", 409);
        body.put("gate", e.gate());
        body.put("alertId", e.alertId());
        body.put("detail", "held pending sanctions review");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
