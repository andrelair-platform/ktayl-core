package com.ktayl.core.billing.payment;

import java.util.UUID;

/**
 * In-process event: a payment was captured (the verified {@code payment_intent.succeeded} webhook). This is
 * the trigger the GL posting listens for (BILL-014: DR Cash / CR Premium Receivable).
 */
public record PaymentCaptured(UUID paymentId, UUID installmentId, long amountMinor, String currency) {}
