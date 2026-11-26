package com.ktayl.core.finance.infrastructure.reserving;

import java.util.List;

/**
 * A balanced double-entry Journal Entry for an IBNR reserve-movement posting (FIN-03, ADR-022). Money is
 * eurocents (never a float). Σ debits == Σ credits is enforced at construction, so an unbalanced JE can never
 * be built. Self-contained to the finance module (a near-copy of the claims/billing JournalEntry — WET-first,
 * ModularityTests forbids reaching into another module's internal ledger package).
 */
public record ReserveJournalEntry(String remark, String currency, List<Line> lines) {

    public record Line(String account, long debitMinor, long creditMinor) {}

    public ReserveJournalEntry {
        long debits = lines.stream().mapToLong(Line::debitMinor).sum();
        long credits = lines.stream().mapToLong(Line::creditMinor).sum();
        if (lines.isEmpty() || debits == 0) {
            throw new IllegalStateException("empty journal entry");
        }
        if (debits != credits) {
            throw new IllegalStateException("unbalanced journal entry: debits=" + debits + " credits=" + credits);
        }
    }
}
