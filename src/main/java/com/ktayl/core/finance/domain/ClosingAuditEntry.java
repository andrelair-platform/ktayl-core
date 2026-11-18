package com.ktayl.core.finance.domain;

import java.time.Instant;
import java.time.YearMonth;

/**
 * One immutable close/reopen audit entry (FINCLOSE-02). For a {@code CLOSED} entry the snapshot fields hold
 * the reconciliation figures attested at close time; for a {@code REOPENED} entry they are null (a reopen
 * carries no snapshot). Money is minor units (eurocents).
 */
public record ClosingAuditEntry(
        YearMonth period,
        Action action,
        String actor,
        Instant at,
        Long subledgerPostedMinor,
        Long glPostedMinor,
        Long driftMinor,
        String glStatus,
        boolean forced,
        String reason) {

    public enum Action {
        CLOSED,
        REOPENED
    }
}
