package com.ktayl.core.finance.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** Outbound port to the append-only close/reopen audit (FINCLOSE-02). Entries are appended, never mutated. */
public interface ClosingAuditPort {

    /** Append one immutable audit entry. */
    void append(ClosingAuditEntry entry);

    /** The period's close/reopen history, oldest first (empty if never closed). */
    List<ClosingAuditEntry> history(YearMonth period);

    /** The timestamp of the period's most recent CLOSED entry, if any. */
    Optional<Instant> lastClosedAt(YearMonth period);
}
