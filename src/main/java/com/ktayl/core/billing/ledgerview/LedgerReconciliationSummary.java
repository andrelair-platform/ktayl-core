package com.ktayl.core.billing.ledgerview;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * Published read model (FINCLOSE-01): the GL outbox for a period, aggregated into buckets keyed by
 * ({@code eventType}, view {@code status}). Money is minor units (eurocents), {@code long} — never a float.
 * {@code currencies} is every distinct currency seen among the period's postings, so the consumer can
 * enforce its single-currency guard (a non-EUR posting is surfaced, never silently summed/dropped).
 *
 * <p>The convenience accessors are pure folds over {@code buckets}; the record carries no behaviour beyond
 * summation, so it stays a stable data contract.
 */
public record LedgerReconciliationSummary(YearMonth period, List<Bucket> buckets, Set<String> currencies) {

    /** Count + total (minor units) of postings for one ({@code eventType}, view {@code status}) pair. */
    public record Bucket(String eventType, String status, long count, long totalMinor) {}

    /** Total minor units for an event type across all statuses (e.g. all premium invoiced in the period). */
    public long totalMinor(String eventType) {
        return buckets.stream().filter(b -> b.eventType().equals(eventType))
                .mapToLong(Bucket::totalMinor).sum();
    }

    /** Total minor units across all event types for a view status (e.g. everything POSTED = the GL-side total). */
    public long totalMinorByStatus(String status) {
        return buckets.stream().filter(b -> b.status().equals(status))
                .mapToLong(Bucket::totalMinor).sum();
    }

    /** Count across all event types for a view status (e.g. how many postings are FAILED). */
    public long countByStatus(String status) {
        return buckets.stream().filter(b -> b.status().equals(status))
                .mapToLong(Bucket::count).sum();
    }
}
