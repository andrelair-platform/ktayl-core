package com.ktayl.core.finance.api;

import java.time.Instant;

/**
 * Published read model (FINCLOSE-02): one entry in a period's close/reopen history. For a {@code CLOSED}
 * entry the snapshot fields hold the figures attested at close; for {@code REOPENED} they are null. Money is
 * minor units (eurocents).
 */
public record PeriodCloseRecord(
        String period,
        String action,
        String actor,
        Instant at,
        Long subledgerPostedMinor,
        Long glPostedMinor,
        Long driftMinor,
        String glStatus,
        boolean forced,
        String reason) {
}
