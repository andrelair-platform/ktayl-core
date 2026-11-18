package com.ktayl.core.finance.api;

import java.time.Instant;
import java.util.List;

/**
 * Published read model (FINCLOSE-01): the GL reconciliation for a period. Money is minor units (eurocents).
 *
 * <p>The two numbers a reviewer reads first are {@code driftMinor} (0 = the GL agrees with the subledger) and
 * the stuck count ({@code pendingCount} + {@code failedCount}). {@code glStatus} is {@code RECONCILED} (drift
 * 0), {@code DRIFT} (non-zero), or {@code UNAVAILABLE} (the GL could not be read — then {@code glPostedMinor}
 * is null and {@code driftMinor} is 0, i.e. not computable). {@code unexpectedCurrencies} is non-empty only if
 * a non-EUR posting appeared (the single-currency guard surfaces it rather than silently mixing currencies).
 *
 * <p>{@code lastClosedAt} is when the period was last closed (null if never, FINCLOSE-02).
 * {@code postCloseMovement} is {@code true} iff a posting attributed to a CLOSED period reached the GL after
 * {@code lastClosedAt} — the integrity signal that something moved after close (expected {@code false}).
 */
public record PeriodReconciliation(
        String period,
        String periodStatus,
        long subledgerPostedMinor,
        Long glPostedMinor,
        String glStatus,
        long driftMinor,
        long pendingCount,
        long pendingMinor,
        long failedCount,
        long failedMinor,
        List<String> unexpectedCurrencies,
        Instant lastClosedAt,
        boolean postCloseMovement) {
}
