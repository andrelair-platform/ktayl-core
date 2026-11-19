package com.ktayl.core.billing.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktayl.core.billing.persistence.LedgerOutboxRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** L1 — the oldest-pending-age gauge logic (FINCLOSE-03): null → 0, else seconds since, never negative. */
class LedgerOutboxMetricsTest {

    private final LedgerOutboxRepository outbox = mock(LedgerOutboxRepository.class);

    @Test
    void oldest_pending_age_is_zero_when_outbox_is_clear() {
        when(outbox.findOldestPendingCreatedAt()).thenReturn(null);
        assertThat(LedgerOutboxMetrics.oldestPendingAgeSeconds(outbox)).isZero();
    }

    @Test
    void oldest_pending_age_is_seconds_since_the_oldest_unposted_row() {
        when(outbox.findOldestPendingCreatedAt()).thenReturn(Instant.now().minusSeconds(120));
        assertThat(LedgerOutboxMetrics.oldestPendingAgeSeconds(outbox)).isBetween(115d, 125d);
    }

    @Test
    void oldest_pending_age_never_negative_for_a_future_timestamp() {
        when(outbox.findOldestPendingCreatedAt()).thenReturn(Instant.now().plusSeconds(60));
        assertThat(LedgerOutboxMetrics.oldestPendingAgeSeconds(outbox)).isZero();
    }
}
