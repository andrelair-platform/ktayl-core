package com.ktayl.core.claimsgl.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktayl.core.claimsgl.ledger.ClaimJournalEntryFactory;
import com.ktayl.core.claimsgl.persistence.ClaimLedgerOutboxEntity;
import com.ktayl.core.claimsgl.persistence.ClaimLedgerOutboxRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** L1 — the outbox write: event→outbox mapping + idempotency by event id + validation rejects poison. */
class ClaimGlServiceTest {

    private final ClaimLedgerOutboxRepository outbox = mock(ClaimLedgerOutboxRepository.class);
    private final ClaimGlService svc = new ClaimGlService(outbox);

    private static ClaimFinancialEvent ev(String id, String type, long amount) {
        return new ClaimFinancialEvent(id, type, "CLM-1", "POL-1", amount, "EUR", LocalDate.of(2026, 1, 1));
    }

    @Test
    void records_a_reserve_movement_as_an_outbox_row() {
        when(outbox.existsByReference("evt-1")).thenReturn(false);
        boolean created = svc.record(ev("evt-1", ClaimFinancialEvent.TYPE_RESERVE_SET, -75_000));
        assertThat(created).isTrue();
        ArgumentCaptor<ClaimLedgerOutboxEntity> cap = ArgumentCaptor.forClass(ClaimLedgerOutboxEntity.class);
        verify(outbox).save(cap.capture());
        assertThat(cap.getValue().getEventType()).isEqualTo(ClaimJournalEntryFactory.RESERVE_MOVEMENT);
        assertThat(cap.getValue().getAmountMinor()).isEqualTo(-75_000L); // signed movement preserved
        assertThat(cap.getValue().getReference()).isEqualTo("evt-1");
    }

    @Test
    void maps_a_payment_to_CLAIM_PAYMENT() {
        when(outbox.existsByReference("evt-2")).thenReturn(false);
        svc.record(ev("evt-2", ClaimFinancialEvent.TYPE_PAYMENT_EXECUTED, 300_000));
        ArgumentCaptor<ClaimLedgerOutboxEntity> cap = ArgumentCaptor.forClass(ClaimLedgerOutboxEntity.class);
        verify(outbox).save(cap.capture());
        assertThat(cap.getValue().getEventType()).isEqualTo(ClaimJournalEntryFactory.CLAIM_PAYMENT);
    }

    @Test
    void is_idempotent_on_a_redelivered_event_id() {
        when(outbox.existsByReference("evt-dup")).thenReturn(true);
        boolean created = svc.record(ev("evt-dup", ClaimFinancialEvent.TYPE_RESERVE_SET, 10_000));
        assertThat(created).isFalse();
        verify(outbox, never()).save(any());
    }
}
