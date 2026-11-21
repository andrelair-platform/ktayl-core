package com.ktayl.core.billing.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktayl.core.billing.invoicing.PremiumInvoiced;
import com.ktayl.core.billing.persistence.LedgerOutboxEntity;
import com.ktayl.core.billing.persistence.LedgerOutboxRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * L2 — the GL transactional outbox against a REAL Postgres: the drainer posts a balanced JE via the ledger
 * client and marks the row posted; a GL outage leaves the row pending (billing never blocked); posting is
 * idempotent; and — the whole point of the outbox — the {@code @EventListener} writes the outbox row in the
 * SAME transaction as the business fact, so a rolled-back business tx leaves NO ledger intent (never a
 * half-post). The ledger client is a recording fake (the ERPNext wire contract is the L3 test).
 */
@SpringBootTest(properties = "ledger.drain-initial-ms=3600000") // don't let the @Scheduled drainer self-fire mid-test
@Testcontainers
@Import(LedgerOutboxIntegrationTest.Fakes.class)
class LedgerOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired LedgerOutboxRepository outbox;
    @Autowired LedgerOutboxDrainer drainer;
    @Autowired RecordingLedger ledger;
    @Autowired TxPublisher tx;

    @BeforeEach
    void reset() {
        ledger.fail = false;
        ledger.calls.set(0);
        outbox.deleteAll();
    }

    private LedgerOutboxEntity pending(String ref, long amt) {
        return outbox.save(new LedgerOutboxEntity(UUID.randomUUID(), JournalEntryFactory.PREMIUM_INVOICED, ref, amt, "EUR", Instant.now()));
    }

    @Test
    void drains_a_pending_posting_to_a_balanced_je_and_marks_it_posted() {
        var row = pending("POL-DRAIN", 480_000);

        drainer.drain();

        assertThat(ledger.lastEntry.totalMinor()).isEqualTo(480_000);        // balanced JE built
        assertThat(ledger.lastRef).isEqualTo(row.getId().toString());         // idempotency ref = outbox id
        var posted = outbox.findById(row.getId()).orElseThrow();
        assertThat(posted.getStatus()).isEqualTo("posted");
        assertThat(posted.getPostedRef()).isEqualTo("JE-" + row.getId());
    }

    @Test
    void a_gl_outage_leaves_the_row_pending_and_records_the_failure() {
        ledger.fail = true;
        var row = pending("POL-DOWN", 480_000);

        drainer.drain(); // must NOT throw — billing is never blocked by a GL blip

        var still = outbox.findById(row.getId()).orElseThrow();
        assertThat(still.getStatus()).isEqualTo("pending");
        assertThat(still.getAttempts()).isEqualTo(1);
        assertThat(still.getLastError()).isNotBlank();
    }

    @Test
    void does_not_repost_an_already_posted_row() {
        pending("POL-ONCE", 480_000);
        drainer.drain();
        int after1 = ledger.calls.get();

        drainer.drain(); // nothing pending now
        assertThat(ledger.calls.get()).isEqualTo(after1); // no second post
    }

    @Test
    void the_listener_writes_the_outbox_row_in_the_business_transaction_atomically() {
        // committed business tx → the outbox row is there
        tx.publishInTx(new PremiumInvoiced("POL-OK", UUID.randomUUID(), 120_000, 102_000, 18_000, "EUR"), false);
        assertThat(outbox.existsByEventTypeAndReference(JournalEntryFactory.PREMIUM_INVOICED, "POL-OK")).isTrue();

        // rolled-back business tx → NO outbox row (same-tx: never a half-post)
        assertThatThrownBy(() ->
                tx.publishInTx(new PremiumInvoiced("POL-ROLLBACK", UUID.randomUUID(), 120_000, 102_000, 18_000, "EUR"), true))
                .isInstanceOf(RuntimeException.class);
        assertThat(outbox.existsByEventTypeAndReference(JournalEntryFactory.PREMIUM_INVOICED, "POL-ROLLBACK")).isFalse();
    }

    // --- test doubles ---

    @TestConfiguration
    static class Fakes {
        @Bean @Primary RecordingLedger recordingLedger() { return new RecordingLedger(); }
        @Bean TxPublisher txPublisher(ApplicationEventPublisher p) { return new TxPublisher(p); }
    }

    static class RecordingLedger implements LedgerClient {
        volatile boolean fail = false;
        volatile JournalEntry lastEntry;
        volatile String lastRef;
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public String postJournalEntry(JournalEntry entry, String idempotencyRef) {
            calls.incrementAndGet();
            if (fail) throw new RuntimeException("ERPNext unreachable");
            this.lastEntry = entry;
            this.lastRef = idempotencyRef;
            return "JE-" + idempotencyRef;
        }
    }

    @Service
    static class TxPublisher {
        private final ApplicationEventPublisher publisher;
        TxPublisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        @Transactional
        public void publishInTx(Object event, boolean rollback) {
            publisher.publishEvent(event); // GlPostingListener runs HERE, in this tx
            if (rollback) throw new RuntimeException("business tx rolled back");
        }
    }
}
