package com.ktayl.core.finance.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A maker-checker reserve review (FIN-03). {@code state} is {@code PENDING|APPROVED|REJECTED}; {@code checker}
 * and {@code decidedAt} are set on decision; {@code bookedMovementMinor} is the signed IBNR movement booked to
 * the GL on approval (null until approved). The {@code estimate} carries the per-accident-period breakdown.
 */
public record ReserveReviewView(UUID id, String portfolio, LocalDate asOf, String basis, String method,
        String currency, long latestMinor, long estimatedUltimateMinor, long estimatedIbnrMinor, String state,
        String maker, String checker, Long bookedMovementMinor, String reason, Instant createdAt,
        Instant decidedAt, IbnrEstimateView estimate) {}
