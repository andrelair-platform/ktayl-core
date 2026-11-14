package com.ktayl.core.billing.payment;

/**
 * The payment gateway isn't configured (dev, no Stripe key) or is transiently unreachable. Maps to 503 —
 * the caller may retry; the installment stays unpaid (no partial state). Not a 5xx bug, an availability signal.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {
    public PaymentGatewayUnavailableException(String message) {
        super(message);
    }

    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
