package com.ktayl.core.billing.payment;

import java.util.UUID;

/** In-process event: a payment was initiated (PaymentIntent created, status pending). Billing-internal. */
public record PaymentInitiated(UUID paymentId, UUID installmentId, String clientKey, long amountMinor, String currency) {}
