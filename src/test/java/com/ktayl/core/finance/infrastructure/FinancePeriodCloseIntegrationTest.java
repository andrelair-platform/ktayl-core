package com.ktayl.core.finance.infrastructure;

import static com.ktayl.core.billing.ledgerview.LedgerReconciliationView.EVENT_PAYMENT_CAPTURED;
import static com.ktayl.core.billing.ledgerview.LedgerReconciliationView.EVENT_PREMIUM_INVOICED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktayl.core.billing.persistence.LedgerOutboxEntity;
import com.ktayl.core.billing.persistence.LedgerOutboxRepository;
import com.ktayl.core.finance.api.FinanceCloseOperations;
import com.ktayl.core.finance.api.PeriodCloseOperations;
import com.ktayl.core.finance.api.PeriodCloseRecord;
import com.ktayl.core.finance.domain.IllegalPeriodTransitionException;
import com.ktayl.core.finance.domain.PeriodNotClosableException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * L2 — the period close/reopen over a REAL Postgres (FINCLOSE-02): settle → close → an immutable audit row +
 * CLOSED status; an unsettled period is refused (409); reopen flips to OPEN + appends history; double-close
 * conflicts; and post-close movement is detected. The GL read is unconfigured (UNAVAILABLE), so a settled
 * period closes on the subledger. Each test uses its OWN period so the registry/audit don't need per-test
 * cleanup; the billing outbox is cleared each test. All periods are in the past, so "ended" holds.
 */
@SpringBootTest(properties = {"ledger.drain-initial-ms=3600000", "ledger.drain-interval-ms=3600000"})
@Testcontainers
class FinancePeriodCloseIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired PeriodCloseOperations periodClose;
    @Autowired FinanceCloseOperations finance;
    @Autowired LedgerOutboxRepository outbox;

    @BeforeEach
    void reset() {
        outbox.deleteAll();
    }

    @Test
    void close_a_settled_period_records_an_immutable_audit_and_marks_it_closed() {
        YearMonth p = YearMonth.of(2026, 10);
        Instant in = Instant.parse("2026-10-10T10:00:00Z");
        outbox.save(posted(EVENT_PREMIUM_INVOICED, 300_000, in));
        outbox.save(posted(EVENT_PAYMENT_CAPTURED, 200_000, in)); // settled; subledger-posted = 500000

        periodClose.close(p, false, null, "officer.sophie");

        var r = finance.reconcile(p);
        assertThat(r.periodStatus()).isEqualTo("CLOSED");
        assertThat(r.lastClosedAt()).isNotNull();
        assertThat(r.postCloseMovement()).isFalse();

        var history = periodClose.closeHistory(p);
        assertThat(history).hasSize(1);
        PeriodCloseRecord e = history.get(0);
        assertThat(e.action()).isEqualTo("CLOSED");
        assertThat(e.actor()).isEqualTo("officer.sophie");
        assertThat(e.subledgerPostedMinor()).isEqualTo(500_000L);
        assertThat(e.glStatus()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void an_unsettled_period_cannot_be_closed() {
        YearMonth p = YearMonth.of(2026, 9);
        outbox.save(failed(EVENT_PAYMENT_CAPTURED, 90_000, Instant.parse("2026-09-10T10:00:00Z")));

        assertThatThrownBy(() -> periodClose.close(p, false, null, "officer"))
                .isInstanceOf(PeriodNotClosableException.class);
        assertThat(finance.reconcile(p).periodStatus()).isEqualTo("OPEN");
    }

    @Test
    void reopen_flips_to_open_and_appends_history() {
        YearMonth p = YearMonth.of(2026, 8);
        outbox.save(posted(EVENT_PREMIUM_INVOICED, 100_000, Instant.parse("2026-08-10T10:00:00Z")));

        periodClose.close(p, false, null, "officer");
        periodClose.reopen(p, "post-close correction", "officer");

        assertThat(finance.reconcile(p).periodStatus()).isEqualTo("OPEN");
        assertThat(periodClose.closeHistory(p)).extracting(PeriodCloseRecord::action)
                .containsExactly("CLOSED", "REOPENED");
    }

    @Test
    void closing_twice_conflicts() {
        YearMonth p = YearMonth.of(2026, 7);
        outbox.save(posted(EVENT_PREMIUM_INVOICED, 100_000, Instant.parse("2026-07-10T10:00:00Z")));

        periodClose.close(p, false, null, "officer");
        assertThatThrownBy(() -> periodClose.close(p, false, null, "officer"))
                .isInstanceOf(IllegalPeriodTransitionException.class);
    }

    @Test
    void post_close_movement_is_detected_when_a_posting_reaches_the_gl_after_close() {
        YearMonth p = YearMonth.of(2026, 6);
        Instant in = Instant.parse("2026-06-10T10:00:00Z");
        outbox.save(posted(EVENT_PREMIUM_INVOICED, 100_000, in)); // settled → closeable

        periodClose.close(p, false, null, "officer");
        Instant closedAt = finance.reconcile(p).lastClosedAt();

        // a posting attributed to the now-CLOSED period, posted to the GL AFTER the close
        LedgerOutboxEntity late = new LedgerOutboxEntity(UUID.randomUUID(), EVENT_PREMIUM_INVOICED,
                "ref-" + UUID.randomUUID(), 50_000, "EUR", in);
        late.markPosted("JE-late", closedAt.plusSeconds(1));
        outbox.save(late);

        assertThat(finance.reconcile(p).postCloseMovement()).isTrue();
    }

    private static LedgerOutboxEntity posted(String eventType, long minor, Instant at) {
        LedgerOutboxEntity e = new LedgerOutboxEntity(UUID.randomUUID(), eventType, "ref-" + UUID.randomUUID(),
                minor, "EUR", at);
        e.markPosted("JE-" + UUID.randomUUID(), at);
        return e;
    }

    private static LedgerOutboxEntity failed(String eventType, long minor, Instant at) {
        LedgerOutboxEntity e = new LedgerOutboxEntity(UUID.randomUUID(), eventType, "ref-" + UUID.randomUUID(),
                minor, "EUR", at);
        e.recordFailure("GL blip"); // attempts > 0, still pending → FAILED (stuck)
        return e;
    }
}
