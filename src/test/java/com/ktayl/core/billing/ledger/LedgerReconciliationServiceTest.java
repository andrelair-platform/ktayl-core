package com.ktayl.core.billing.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktayl.core.billing.ledgerview.LedgerReconciliationSummary;
import com.ktayl.core.billing.ledgerview.LedgerReconciliationView;
import com.ktayl.core.billing.persistence.LedgerOutboxEntity;
import com.ktayl.core.billing.persistence.LedgerOutboxRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * L1 — billing's ledger reconciliation view: the UTC month window (AC-2) + the pending/posted/failed
 * classification + aggregation. The repository is stubbed; no Spring, no DB (the real-Postgres path is the
 * finance L2 integration test).
 */
class LedgerReconciliationServiceTest {

    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private final LedgerOutboxRepository repo = mock(LedgerOutboxRepository.class);
    private final LedgerReconciliationService service = new LedgerReconciliationService(repo);

    @Test
    void window_is_the_half_open_utc_month() {
        assertThat(LedgerReconciliationService.startOf(OCT))
                .isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(LedgerReconciliationService.startOf(OCT.plusMonths(1)))
                .isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    void summarize_queries_the_correct_month_window() {
        when(repo.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(List.of());

        service.summarize(OCT);

        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        verify(repo).findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start.capture(), end.capture());
        assertThat(start.getValue()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(end.getValue()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z")); // exclusive upper bound
    }

    @Test
    void classifies_posted_pending_failed_and_aggregates_by_event_type() {
        Instant t = Instant.parse("2026-10-15T10:00:00Z");
        when(repo.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(List.of(
                posted(LedgerReconciliationView.EVENT_PREMIUM_INVOICED, 300_000, t),
                posted(LedgerReconciliationView.EVENT_PREMIUM_INVOICED, 180_000, t),
                pending(LedgerReconciliationView.EVENT_PREMIUM_INVOICED, 120_000, t),
                failed(LedgerReconciliationView.EVENT_PAYMENT_CAPTURED, 90_000, t)));

        LedgerReconciliationSummary s = service.summarize(OCT);

        assertThat(s.period()).isEqualTo(OCT);
        assertThat(s.currencies()).containsExactly("EUR");
        // posted premium = 480000; the whole posted total (any event type) = 480000
        assertThat(s.totalMinorByStatus(LedgerReconciliationView.STATUS_POSTED)).isEqualTo(480_000L);
        assertThat(s.totalMinor(LedgerReconciliationView.EVENT_PREMIUM_INVOICED)).isEqualTo(600_000L); // 480k + 120k pending
        assertThat(s.totalMinorByStatus(LedgerReconciliationView.STATUS_PENDING)).isEqualTo(120_000L);
        assertThat(s.countByStatus(LedgerReconciliationView.STATUS_FAILED)).isEqualTo(1L);
        assertThat(s.totalMinorByStatus(LedgerReconciliationView.STATUS_FAILED)).isEqualTo(90_000L);
    }

    private static LedgerOutboxEntity pending(String eventType, long minor, Instant at) {
        return new LedgerOutboxEntity(UUID.randomUUID(), eventType, "ref", minor, "EUR", at);
    }

    private static LedgerOutboxEntity posted(String eventType, long minor, Instant at) {
        LedgerOutboxEntity e = pending(eventType, minor, at);
        e.markPosted("JE-" + minor, at);
        return e;
    }

    private static LedgerOutboxEntity failed(String eventType, long minor, Instant at) {
        LedgerOutboxEntity e = pending(eventType, minor, at); // still 'pending' DB status…
        e.recordFailure("GL blip");                            // …but attempts > 0 → view status FAILED
        return e;
    }
}
