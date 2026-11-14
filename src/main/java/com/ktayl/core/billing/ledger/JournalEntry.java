package com.ktayl.core.billing.ledger;

import java.util.List;

/**
 * A balanced double-entry Journal Entry (BILL-014). Money is eurocents (minor units, never a float). The
 * invariant — Σ debits == Σ credits, and the entry is non-empty — is enforced at construction, so an
 * unbalanced JE can never be built, let alone posted to the GL.
 */
public record JournalEntry(String remark, String currency, List<Line> lines) {

    /** One posting line: exactly one of debit/credit is non-zero (eurocents). */
    public record Line(String account, long debitMinor, long creditMinor) {}

    public JournalEntry {
        long debits = lines.stream().mapToLong(Line::debitMinor).sum();
        long credits = lines.stream().mapToLong(Line::creditMinor).sum();
        if (lines.isEmpty() || debits == 0) {
            throw new IllegalStateException("empty journal entry");
        }
        if (debits != credits) {
            throw new IllegalStateException("unbalanced journal entry: debits=" + debits + " credits=" + credits);
        }
    }

    /** The entry total (Σ debits == Σ credits), eurocents. */
    public long totalMinor() {
        return lines.stream().mapToLong(Line::debitMinor).sum();
    }
}
