package com.ktayl.core.claimsgl.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktayl.core.claimsgl.ledger.JournalEntry.Line;
import com.ktayl.core.claimsgl.persistence.ClaimLedgerOutboxEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** L1 — the claims GL double-entry mapping + the balance invariant (an unbalanced JE can never be built). */
class ClaimJournalEntryFactoryTest {

    private final ClaimJournalEntryFactory factory =
            new ClaimJournalEntryFactory("Claims Incurred", "Outstanding Claims Provision", "Cash");

    private static ClaimLedgerOutboxEntity row(String type, long amt) {
        return new ClaimLedgerOutboxEntity(UUID.randomUUID(), type, "EVT-1", "CLM-1", amt, "EUR", Instant.now());
    }

    @Test
    void a_reserve_increase_is_DR_incurred_CR_provision() {
        JournalEntry je = factory.from(row(ClaimJournalEntryFactory.RESERVE_MOVEMENT, 500_000));
        assertThat(je.totalMinor()).isEqualTo(500_000);
        assertThat(je.lines()).containsExactly(
                new Line("Claims Incurred", 500_000, 0),
                new Line("Outstanding Claims Provision", 0, 500_000));
    }

    @Test
    void a_reserve_downward_adjustment_reverses_the_legs() {
        // a release: reserve moves down by 120_000 → DR provision / CR incurred (the reverse)
        JournalEntry je = factory.from(row(ClaimJournalEntryFactory.RESERVE_MOVEMENT, -120_000));
        assertThat(je.totalMinor()).isEqualTo(120_000);
        assertThat(je.lines()).containsExactly(
                new Line("Outstanding Claims Provision", 120_000, 0),
                new Line("Claims Incurred", 0, 120_000));
    }

    @Test
    void a_payment_is_DR_provision_CR_cash() {
        JournalEntry je = factory.from(row(ClaimJournalEntryFactory.CLAIM_PAYMENT, 300_000));
        assertThat(je.lines()).containsExactly(
                new Line("Outstanding Claims Provision", 300_000, 0),
                new Line("Cash", 0, 300_000));
    }

    @Test
    void an_unbalanced_entry_is_rejected_at_construction() {
        assertThatThrownBy(() -> new JournalEntry("x", "EUR",
                List.of(new Line("A", 100, 0), new Line("B", 0, 50))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unbalanced");
    }

    @Test
    void an_unknown_event_type_throws() {
        assertThatThrownBy(() -> factory.from(row("NONSENSE", 100)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
