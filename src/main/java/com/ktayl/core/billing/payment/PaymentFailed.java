package com.ktayl.core.billing.payment;

import java.util.UUID;

/** In-process event: a premium payment attempt failed (the SEPA debit bounced). The installment stays open. */
public record PaymentFailed(UUID paymentId, UUID installmentId, String policyNumber, String reason) {}
