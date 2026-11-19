package com.ktayl.core.finance.infrastructure;

import static com.ktayl.core.billing.ledgerview.LedgerReconciliationView.EVENT_PAYMENT_CAPTURED;
import static com.ktayl.core.billing.ledgerview.LedgerReconciliationView.EVENT_PREMIUM_INVOICED;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktayl.core.billing.persistence.LedgerOutboxEntity;
import com.ktayl.core.billing.persistence.LedgerOutboxRepository;
import com.ktayl.core.finance.api.FinanceCloseOperations;
import com.ktayl.core.finance.api.PeriodReconciliation;
import com.ktayl.core.finance.api.PremiumRegister;
import com.ktayl.core.finance.domain.GeneralLedgerReadPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.YearMonth;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * L2 — the finance period close over a REAL Postgres (FINCLOSE-01 AC-2/4/5): seed billing's {@code
 * ledger_outbox} across statuses + months, then assert the finance reconciliation + register through the
 * published port. Period attribution is proven (a September row is excluded from October). The GL read port is
 * a toggleable fake (empty = unavailable; a value = reconciled/drift) — the real ERPNext read is L3/live, per
 * the house "fake at the boundary" convention. The {@code @Scheduled} drainer is parked so it can't mutate the
 * seeded rows.
 */
@SpringBootTest(properties = {"ledger.drain-initial-ms=3600000", "ledger.drain-interval-ms=3600000"})
@Testcontainers
@Import(FinanceReconciliationIntegrationTest.FakeGl.class)
class FinanceReconciliationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired FinanceCloseOperations finance;
    @Autowired LedgerOutboxRepository outbox;
    @Autowired ToggleableGl gl;
    @Autowired MeterRegistry meters;

    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private static final Instant IN_OCT = Instant.parse("2026-10-15T10:00:00Z");
    private static final Instant IN_SEP = Instant.parse("2026-09-20T10:00:00Z");

    @BeforeEach
    void reset() {
        outbox.deleteAll();
        gl.total = OptionalLong.empty();
    }

    @Test
    void reconciles_subledger_over_real_postgres_with_gl_unavailable() {
        outbox.save(posted(EVENT_PREMIUM_INVOICED, 300_000, IN_OCT));
        outbox.save(posted(EVENT_PAYMENT_CAPTURED, 180_000, IN_OCT)); // subledger posted = 480000
        outbox.save(pending(EVENT_PREMIUM_INVOICED, 120_000, IN_OCT));
        outbox.save(failed(EVENT_PAYMENT_CAPTURED, 90_000, IN_OCT));
        outbox.save(posted(EVENT_PREMIUM_INVOICED, 999_999, IN_SEP)); // a Sept row MUST NOT count in Oct

        PeriodReconciliation r = finance.reconcile(OCT);

        assertThat(r.subledgerPostedMinor()).isEqualTo(480_000L);
        assertThat(r.pendingCount()).isEqualTo(1L);
        assertThat(r.pendingMinor()).isEqualTo(120_000L);
        assertThat(r.failedCount()).isEqualTo(1L);
        assertThat(r.failedMinor()).isEqualTo(90_000L);
        assertThat(r.glStatus()).isEqualTo("UNAVAILABLE");
        assertThat(r.periodStatus()).isEqualTo("OPEN"); // no accounting_period row → implicitly open
        assertThat(r.unexpectedCurrencies()).isEmpty();
    }

    @Test
    void register_ties_out_over_real_postgres() {
        outbox.save(posted(EVENT_PREMIUM_INVOICED, 300_000, IN_OCT));
        outbox.save(pending(EVENT_PREMIUM_INVOICED, 120_000, IN_OCT)); // invoiced = 420000
        outbox.save(posted(EVENT_PAYMENT_CAPTURED, 250_000, IN_OCT));  // received = 250000

        PremiumRegister reg = finance.register(OCT);

        assertThat(reg.grossPremiumInvoicedMinor()).isEqualTo(420_000L);
        assertThat(reg.cashReceivedMinor()).isEqualTo(250_000L);
        assertThat(reg.receivableOutstandingMinor()).isEqualTo(170_000L);
        assertThat(reg.currency()).isEqualTo("EUR");
    }

    @Test
    void gl_available_drives_reconciled_then_drift() {
        outbox.save(posted(EVENT_PREMIUM_INVOICED, 480_000, IN_OCT)); // subledger posted = 480000

        gl.total = OptionalLong.of(480_000L);
        assertThat(finance.reconcile(OCT).glStatus()).isEqualTo("RECONCILED");

        gl.total = OptionalLong.of(400_000L);
        PeriodReconciliation drift = finance.reconcile(OCT);
        assertThat(drift.glStatus()).isEqualTo("DRIFT");
        assertThat(drift.driftMinor()).isEqualTo(80_000L);
    }

    private static LedgerOutboxEntity pending(String eventType, long minor, Instant at) {
        // reference must be unique — the outbox has UNIQUE(event_type, reference)
        return new LedgerOutboxEntity(UUID.randomUUID(), eventType, "ref-" + UUID.randomUUID(), minor, "EUR", at);
    }

    @Test
    void gl_outbox_gauges_and_the_dashboard_over_real_postgres() {
        Instant in = Instant.parse("2026-10-10T10:00:00Z");
        outbox.save(posted(EVENT_PREMIUM_INVOICED, 100_000, in));
        outbox.save(pending(EVENT_PREMIUM_INVOICED, 50_000, in));  // pending, 0 attempts
        outbox.save(failed(EVENT_PAYMENT_CAPTURED, 30_000, in));   // pending, attempts > 0 → FAILED

        // gauges (FINCLOSE-03): both unposted rows are status=pending (backlog 2); one is stuck (failed 1)
        assertThat(meters.get("ledger.outbox.pending").gauge().value()).isEqualTo(2d);
        assertThat(meters.get("ledger.outbox.failed").gauge().value()).isEqualTo(1d);
        assertThat(meters.get("ledger.outbox.oldest_pending_age_seconds").gauge().value()).isGreaterThan(0d);

        // dashboard: one row per period across the inclusive range, oldest first
        assertThat(finance.dashboard(YearMonth.of(2026, 8), YearMonth.of(2026, 10)))
                .extracting(PeriodReconciliation::period)
                .containsExactly("2026-08", "2026-09", "2026-10");
    }

    private static LedgerOutboxEntity posted(String eventType, long minor, Instant at) {
        LedgerOutboxEntity e = pending(eventType, minor, at);
        e.markPosted("JE-" + UUID.randomUUID(), at);
        return e;
    }

    private static LedgerOutboxEntity failed(String eventType, long minor, Instant at) {
        LedgerOutboxEntity e = pending(eventType, minor, at);
        e.recordFailure("GL blip"); // attempts > 0, still pending → view status FAILED
        return e;
    }

    @TestConfiguration
    static class FakeGl {
        @Bean
        ToggleableGl toggleableGl() {
            return new ToggleableGl();
        }
    }

    /** A GL read fake whose answer the test toggles: empty = unavailable, a value = a known posted total. */
    static class ToggleableGl implements GeneralLedgerReadPort {
        volatile OptionalLong total = OptionalLong.empty();

        @Override
        public OptionalLong postedTotalMinor(YearMonth period) {
            return total;
        }
    }
}
