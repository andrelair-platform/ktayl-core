package com.ktayl.core.billing.payment;

/**
 * Outbound port to the payment PSP (ADR-002: Stripe, test mode). Kept behind an interface so the billing
 * module depends on the capability, not on Stripe — the Stripe SDK lives only in {@link StripePaymentGateway}.
 * The sole implementation is active only when a Stripe secret key is configured (prod); dev leaves it blank,
 * so {@code PaymentService} degrades to a clear 503 rather than calling a PSP that isn't wired.
 */
public interface PaymentGatewayClient {

    /**
     * Create (idempotently, keyed by {@code clientKey}) a SEPA Direct Debit PaymentIntent for the amount.
     * Returns the PSP intent id + its current status; capture happens later via the webhook, not here.
     */
    PspIntent createSepaPaymentIntent(String clientKey, long amountMinor, String currency);

    /** The minimal PSP result the domain needs — never the full Stripe object. */
    record PspIntent(String intentId, String status) {}
}
