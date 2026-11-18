package com.ktayl.core.finance.domain;

/**
 * The period-close money arithmetic — pure, framework-free, minor-units {@code long} (never a float). Tiny by
 * design, but named + unit-tested so the two financial invariants (drift and outstanding) have one home and
 * one test (FINCLOSE-01 AC-7).
 */
public final class CloseArithmetic {

    private CloseArithmetic() {}

    /** GL reconciliation drift: what the subledger says is POSTED minus what the GL holds. 0 = reconciled. */
    public static long drift(long subledgerPostedMinor, long glPostedMinor) {
        return subledgerPostedMinor - glPostedMinor;
    }

    /** Receivable outstanding for the period: premium invoiced minus cash received. */
    public static long outstanding(long invoicedMinor, long receivedMinor) {
        return invoicedMinor - receivedMinor;
    }
}
