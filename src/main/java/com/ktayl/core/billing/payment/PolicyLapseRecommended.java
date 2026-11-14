package com.ktayl.core.billing.payment;

import java.util.UUID;

/**
 * In-process event: premium collection for a policy has failed {@code attempts} times and the dunning case
 * reached the lapse threshold. This is a <b>recommendation for human/regulatory review</b> (IDD / insurance
 * contract law require formal notice before a non-payment lapse) — NOT an automatic policy cancellation.
 */
public record PolicyLapseRecommended(String policyNumber, UUID installmentId, int attempts, String reason) {}
