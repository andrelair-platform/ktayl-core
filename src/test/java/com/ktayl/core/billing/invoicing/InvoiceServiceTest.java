package com.ktayl.core.billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktayl.core.billing.persistence.AuditRepository;
import com.ktayl.core.billing.persistence.IngestedPolicyEntity;
import com.ktayl.core.billing.persistence.IngestedPolicyRepository;
import com.ktayl.core.billing.persistence.InstallmentEntity;
import com.ktayl.core.billing.persistence.InstallmentRepository;
import com.ktayl.core.billing.persistence.InvoiceEntity;
import com.ktayl.core.billing.persistence.InvoiceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** L1 — invoice raising: idempotency, reconciliation (Σ installments = premium), event, not-ingested. */
class InvoiceServiceTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final InstallmentRepository installments = mock(InstallmentRepository.class);
    private final IngestedPolicyRepository ingested = mock(IngestedPolicyRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final InvoiceService svc = new InvoiceService(invoices, installments, ingested, audit, events);

    private static IngestedPolicyEntity policy(long premiumMinor) {
        return new IngestedPolicyEntity("POL-1", premiumMinor, "EUR", "PROPERTY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "SUB-1", "Q-1", "ingested", Instant.now());
    }

    @Test
    void raises_an_invoice_with_reconciling_installments_and_emits_the_event() {
        when(invoices.findByPolicyNumber("POL-1")).thenReturn(Optional.empty());
        when(ingested.findById("POL-1")).thenReturn(Optional.of(policy(100_000L)));

        var result = svc.raiseFor("POL-1", 3);

        assertThat(result.created()).isTrue();
        assertThat(result.invoice().getTotalMinor()).isEqualTo(100_000L);
        verify(invoices).save(any(InvoiceEntity.class));
        verify(events).publishEvent(any(PremiumInvoiced.class));
        verify(audit).save(any());

        // the 3 installments reconcile to the premium, to the cent
        ArgumentCaptor<InstallmentEntity> cap = ArgumentCaptor.forClass(InstallmentEntity.class);
        verify(installments, org.mockito.Mockito.times(3)).save(cap.capture());
        long sum = cap.getAllValues().stream().mapToLong(InstallmentEntity::getAmountMinor).sum();
        assertThat(sum).isEqualTo(100_000L);
    }

    @Test
    void is_idempotent_when_an_invoice_already_exists() {
        var existing = new InvoiceEntity(UUID.randomUUID(), "POL-1", "EUR", 100_000L, "issued", Instant.now());
        when(invoices.findByPolicyNumber("POL-1")).thenReturn(Optional.of(existing));

        var result = svc.raiseFor("POL-1", 1);

        assertThat(result.created()).isFalse();
        assertThat(result.invoice()).isSameAs(existing);
        verify(invoices, never()).save(any());
        verify(installments, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void rejects_a_policy_that_was_never_ingested() {
        when(invoices.findByPolicyNumber("GHOST")).thenReturn(Optional.empty());
        when(ingested.findById("GHOST")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.raiseFor("GHOST", 1)).isInstanceOf(PolicyNotIngestedException.class);
        verify(invoices, never()).save(any());
    }
}
