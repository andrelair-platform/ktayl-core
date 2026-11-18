package com.ktayl.core.billing.ledgerview;

import java.time.YearMonth;

/**
 * Published inbound port (FINCLOSE-01): a read-only reconciliation summary of the GL outbox for an
 * accounting period, for the {@code finance} module's period close. Billing owns the subledger (the
 * {@code ledger_outbox}); this exposes an aggregate view of it, never the rows.
 *
 * <p><b>Period attribution (FINCLOSE-01 AC-2):</b> an outbox posting belongs to the period of its
 * {@code created_at} (UTC → {@link YearMonth}) — the date the billing fact was recorded is its accounting
 * date. The outbox carries no separate business date, and the alternative (joining to the invoice/payment
 * business date) would be a billing-internal concern; keeping attribution here preserves the module
 * boundary. The attribution + aggregation are implemented and unit-tested inside billing.
 *
 * <p><b>View status</b> abstracts billing's raw {@code pending}/{@code posted} DB status into the three
 * states a reconciliation cares about: {@link #STATUS_POSTED} (reached the GL), {@link #STATUS_PENDING}
 * (queued, not yet attempted), {@link #STATUS_FAILED} (queued, drained at least once and still unposted —
 * the "stuck" rows). The consumer never sees the DB status.
 */
public interface LedgerReconciliationView {

    /** Outbox event type: a premium invoiced (DR Premium Receivable / CR Premium Income). */
    String EVENT_PREMIUM_INVOICED = "PREMIUM_INVOICED";

    /** Outbox event type: a payment captured (DR Cash / CR Premium Receivable). */
    String EVENT_PAYMENT_CAPTURED = "PAYMENT_CAPTURED";

    /** View status: posting reached the GL. */
    String STATUS_POSTED = "POSTED";

    /** View status: queued, not yet attempted. */
    String STATUS_PENDING = "PENDING";

    /** View status: queued, attempted at least once and still unposted (a stuck GL entry). */
    String STATUS_FAILED = "FAILED";

    /** The reconciliation summary for {@code period} (never null; empty buckets if the period has no postings). */
    LedgerReconciliationSummary summarize(YearMonth period);
}
