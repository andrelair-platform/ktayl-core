package com.ktayl.core.billing.invoicing;

/**
 * The gross / net / commission breakdown of a premium (all eurocents, minor units — never a float).
 * <ul>
 *   <li>{@code grossMinor} — what the policyholder owes (the UW premium; what we bill and collect)</li>
 *   <li>{@code commissionMinor} — the intermediary's (broker) remuneration, {@code grossMinor * bps}</li>
 *   <li>{@code netMinor} — {@code grossMinor - commissionMinor} = what the insurer actually earns</li>
 *   <li>{@code bps} — the applied commission rate in basis points (1500 bps = 15%)</li>
 * </ul>
 * The split <b>reconciles exactly</b>: {@code netMinor + commissionMinor == grossMinor}, enforced here at
 * construction (mirrored by a DB CHECK). A pure value object — no I/O, fully unit-testable.
 */
public record CommissionSplit(long grossMinor, long commissionMinor, long netMinor, int bps) {

    public CommissionSplit {
        if (grossMinor <= 0) throw new IllegalArgumentException("grossMinor must be > 0");
        if (bps < 0) throw new IllegalArgumentException("bps must be >= 0");
        if (commissionMinor < 0 || netMinor < 0) throw new IllegalArgumentException("split amounts must be >= 0");
        if (commissionMinor + netMinor != grossMinor) {
            throw new IllegalStateException(
                    "split does not reconcile: net=" + netMinor + " + commission=" + commissionMinor + " != gross=" + grossMinor);
        }
    }
}
