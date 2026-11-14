package com.ktayl.core.billing.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktayl.core.billing.ledger.JournalEntry.Line;
import com.ktayl.core.billing.persistence.LedgerOutboxEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** L1 — the GL double-entry mapping + the balance invariant (an unbalanced JE can never be built). */
class JournalEntryFactoryTest {

    private final JournalEntryFactory factory =
            new JournalEntryFactory("Premium Receivable", "Premium Income", "Cash");

    private static LedgerOutboxEntity row(String type, long amt) {
        return new LedgerOutboxEntity(UUID.randomUUID(), type, "REF-1", amt, "EUR", Instant.now());
    }

    @Test
    void premium_invoiced_maps_to_DR_receivable_CR_income() {
        JournalEntry je = factory.from(row(JournalEntryFactory.PREMIUM_INVOICED, 480_000));
        assertThat(je.totalMinor()).isEqualTo(480_000);
        assertThat(je.lines()).containsExactly(
                new Line("Premium Receivable", 480_000, 0),
                new Line("Premium Income", 0, 480_000));
    }

    @Test
    void payment_captured_maps_to_DR_cash_CR_receivable() {
        JournalEntry je = factory.from(row(JournalEntryFactory.PAYMENT_CAPTURED, 480_000));
        assertThat(je.lines()).containsExactly(
                new Line("Cash", 480_000, 0),
                new Line("Premium Receivable", 0, 480_000));
    }

    @Test
    void an_unbalanced_entry_is_rejected_at_construction() {
        assertThatThrownBy(() -> new JournalEntry("x", "EUR",
                List.of(new Line("A", 100, 0), new Line("B", 0, 50))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unbalanced");
    }

    @Test
    void an_empty_entry_is_rejected() {
        assertThatThrownBy(() -> new JournalEntry("x", "EUR", List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void an_unknown_event_type_throws() {
        assertThatThrownBy(() -> factory.from(row("NONSENSE", 100)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
