package com.ktayl.core.billing.payment;

/**
 * The payment can't be initiated against the current state (e.g. the installment is already paid, or the
 * amount would over-pay). A business condition → 409 Conflict (4xx, never a 5xx) — rejected before Stripe.
 */
public class PaymentNotAllowedException extends RuntimeException {
    public PaymentNotAllowedException(String message) {
        super(message);
    }
}
