package com.ktayl.core.finance.api;

import java.time.YearMonth;
import java.util.List;

/**
 * The finance module's published period-close port (FINCLOSE-02) — the lifecycle commands (close / reopen) +
 * the close history. Separate from the read-only {@link FinanceCloseOperations} (reconciliation / register).
 * The close is a control/attestation, not a physical lock on billing (ADR-019).
 */
public interface PeriodCloseOperations {

    /**
     * Close a period. Preconditions (each a conflict): the period has ended, the subledger is settled, and the
     * GL reconciles — a GL drift may be overridden with {@code force} + a {@code reason}. Records an immutable
     * closing-audit entry with the attested figures. {@code actor} is the authenticated principal.
     */
    void close(YearMonth period, boolean force, String reason, String actor);

    /** Reopen a CLOSED period (reason required); records a {@code REOPENED} audit entry. */
    void reopen(YearMonth period, String reason, String actor);

    /** The period's close/reopen history, oldest first (empty if never closed). */
    List<PeriodCloseRecord> closeHistory(YearMonth period);
}
