package com.ktayl.core.billing.ledger;

import com.ktayl.core.billing.persistence.LedgerOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Billing-owned GL-outbox health gauges (FINCLOSE-03), exported on {@code /actuator/prometheus}. These live
 * in billing because billing owns the {@code ledger_outbox} table; finance consumes them via Prometheus (the
 * alert + dashboard), not through a module port — metrics are an operational concern, not a business contract.
 *
 * <ul>
 *   <li>{@code ledger.outbox.pending} — unposted rows (the GL backlog).</li>
 *   <li>{@code ledger.outbox.failed} — unposted rows that have been attempted at least once (stuck). The
 *       {@code FinanceGLOutboxDrift} alert fires on this &gt; 0.</li>
 *   <li>{@code ledger.outbox.oldest_pending_age_seconds} — age of the oldest unposted row (0 when none);
 *       the SLA signal (a posting sitting unposted too long).</li>
 * </ul>
 *
 * <p>Micrometer polls each gauge on scrape; the backing queries are cheap (count / min). Null oldest → 0.
 */
@Component
class LedgerOutboxMetrics {

    LedgerOutboxMetrics(MeterRegistry registry, LedgerOutboxRepository outbox) {
        Gauge.builder("ledger.outbox.pending", outbox, r -> r.countByStatus("pending"))
                .description("Unposted GL outbox rows (backlog)")
                .register(registry);
        Gauge.builder("ledger.outbox.failed", outbox, r -> r.countByStatusAndAttemptsGreaterThan("pending", 0))
                .description("Unposted GL outbox rows that have failed at least one post attempt (stuck)")
                .register(registry);
        Gauge.builder("ledger.outbox.oldest_pending_age_seconds", outbox, LedgerOutboxMetrics::oldestPendingAgeSeconds)
                .baseUnit("seconds")
                .description("Age of the oldest unposted GL outbox row")
                .register(registry);
    }

    /** Seconds since the oldest unposted row was created; 0 when the outbox is clear. Pure (testable). */
    static double oldestPendingAgeSeconds(LedgerOutboxRepository outbox) {
        Instant oldest = outbox.findOldestPendingCreatedAt();
        if (oldest == null) {
            return 0d;
        }
        return Math.max(0L, Duration.between(oldest, Instant.now()).getSeconds());
    }
}
