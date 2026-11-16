package com.ktayl.core.billing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktayl.core.billing.persistence.AuditRepository;
import com.ktayl.core.billing.persistence.InstallmentEntity;
import com.ktayl.core.billing.persistence.InstallmentRepository;
import com.ktayl.core.billing.persistence.InvoiceEntity;
import com.ktayl.core.billing.persistence.InvoiceRepository;
import com.ktayl.core.billing.persistence.PaymentEntity;
import com.ktayl.core.billing.persistence.PaymentRepository;
import com.ktayl.core.policy.api.PolicyQuery;
import com.ktayl.core.policy.api.PolicyView;
import com.ktayl.core.screening.api.PartyRef;
import com.ktayl.core.screening.api.ScreeningGate;
import com.ktayl.core.screening.api.ScreeningOutcome;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

/**
 * L1 — payment initiation logic: idempotency by client_key, over-pay rejection (409 before the PSP), the
 * 503 when no gateway is wired, and the happy path (intent created, payment persisted pending, event).
 * The PSP + repos are mocked — the real-collaborator proof is the L2/L3 tests.
 */
class PaymentServiceTest {

    private final InstallmentRepository installments = mock(InstallmentRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PaymentGatewayClient gateway = mock(PaymentGatewayClient.class);
    private final ScreeningGate screening = mock(ScreeningGate.class);
    private final PolicyQuery policies = mock(PolicyQuery.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<PaymentGatewayClient> gatewayProvider = mock(ObjectProvider.class);

    private final PaymentService svc =
            new PaymentService(installments, invoices, payments, audit, events, gatewayProvider, screening, policies);

    private static final UUID INV = UUID.randomUUID();
    private static final UUID INST = UUID.randomUUID();

    /** Default: screening ALLOWs, and the policy read resolves a holder for POL-1. */
    private void allowScreening() {
        when(screening.screen(any(PartyRef.class), anyString(), anyString()))
                .thenReturn(new ScreeningOutcome("ALLOW", null, "v-2026-11-16"));
        when(policies.findByPolicyNumber("POL-1")).thenReturn(Optional.of(new PolicyView(
                UUID.randomUUID(), "POL-1", "Acme Marine SA", "MARINE",
                "ACTIVE", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))));
    }

    private static InstallmentEntity installment(String status) {
        return new InstallmentEntity(INST, INV, 1, LocalDate.of(2026, 1, 1), 120_000L, status);
    }

    private static InvoiceEntity invoice() {
        return new InvoiceEntity(INV, "POL-1", "EUR", 120_000L, "issued", Instant.now());
    }

    @Test
    void initiates_a_pending_payment_creating_a_stripe_intent() {
        allowScreening();
        when(payments.findByClientKey("key-1")).thenReturn(Optional.empty());
        when(installments.findById(INST)).thenReturn(Optional.of(installment("open")));
        when(invoices.findById(INV)).thenReturn(Optional.of(invoice()));
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(gateway.createSepaPaymentIntent("key-1", 120_000L, "EUR"))
                .thenReturn(new PaymentGatewayClient.PspIntent("pi_test_1", "requires_payment_method"));
        when(payments.findById(any())).thenAnswer(i ->
                Optional.of(new PaymentEntity(i.getArgument(0), INST, "key-1", "pi_test_1", 120_000L, "EUR", "pending", Instant.now())));

        PaymentEntity p = svc.initiate(INST, "key-1", "kanmegnea");

        assertThat(p.getStatus()).isEqualTo("pending");
        assertThat(p.getPspIntentId()).isEqualTo("pi_test_1");
        verify(payments).save(any(PaymentEntity.class));
        // ALLOW path screened the holder (resolved via the policy read interface) at the payment gate
        verify(screening).screen(
                argThat(party -> "POL-1".equals(party.externalRef()) && "Acme Marine SA".equals(party.name())),
                eq("payment"), eq("kanmegnea"));
        verify(gateway).createSepaPaymentIntent("key-1", 120_000L, "EUR");
    }

    @Test
    void screening_hold_blocks_the_payment_and_never_calls_the_psp() {
        UUID alert = UUID.randomUUID();
        when(payments.findByClientKey("key-1")).thenReturn(Optional.empty());
        when(installments.findById(INST)).thenReturn(Optional.of(installment("open")));
        when(invoices.findById(INV)).thenReturn(Optional.of(invoice()));
        when(policies.findByPolicyNumber("POL-1")).thenReturn(Optional.of(new PolicyView(
                UUID.randomUUID(), "POL-1", "Acme Marine SA", "MARINE",
                "ACTIVE", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))));
        when(screening.screen(any(PartyRef.class), anyString(), anyString()))
                .thenReturn(new ScreeningOutcome("HOLD", alert, null));

        assertThatThrownBy(() -> svc.initiate(INST, "key-1", "kanmegnea"))
                .isInstanceOf(PaymentScreeningHoldException.class)
                .extracting("alertId", "gate").containsExactly(alert, "payment");

        verify(gatewayProvider, never()).getIfAvailable();
        verify(gateway, never()).createSepaPaymentIntent(anyString(), anyLong(), anyString());
        verify(payments, never()).save(any());
    }

    @Test
    void screens_with_the_policy_number_as_name_when_the_holder_is_unresolvable() {
        when(payments.findByClientKey("key-1")).thenReturn(Optional.empty());
        when(installments.findById(INST)).thenReturn(Optional.of(installment("open")));
        when(invoices.findById(INV)).thenReturn(Optional.of(invoice()));
        when(policies.findByPolicyNumber("POL-1")).thenReturn(Optional.empty()); // no policy row → no name
        when(screening.screen(any(PartyRef.class), anyString(), anyString()))
                .thenReturn(new ScreeningOutcome("ALLOW", null, "v-2026-11-16"));
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(gateway.createSepaPaymentIntent("key-1", 120_000L, "EUR"))
                .thenReturn(new PaymentGatewayClient.PspIntent("pi_test_1", "requires_payment_method"));
        when(payments.findById(any())).thenAnswer(i ->
                Optional.of(new PaymentEntity(i.getArgument(0), INST, "key-1", "pi_test_1", 120_000L, "EUR", "pending", Instant.now())));

        svc.initiate(INST, "key-1", "kanmegnea");

        // does NOT skip screening — falls back to the policy_number as the name so the screen is auditable
        verify(screening).screen(
                argThat(party -> "POL-1".equals(party.externalRef()) && "POL-1".equals(party.name())),
                eq("payment"), eq("kanmegnea"));
    }

    @Test
    void is_idempotent_by_client_key_no_second_intent() {
        var already = new PaymentEntity(UUID.randomUUID(), INST, "key-1", "pi_test_1", 120_000L, "EUR", "pending", Instant.now());
        when(payments.findByClientKey("key-1")).thenReturn(Optional.of(already));

        PaymentEntity p = svc.initiate(INST, "key-1", "kanmegnea");

        assertThat(p).isSameAs(already);
        verify(gateway, never()).createSepaPaymentIntent(anyString(), anyLong(), anyString());
        verify(payments, never()).save(any());
    }

    @Test
    void rejects_payment_on_a_paid_installment_before_calling_the_psp() {
        when(payments.findByClientKey("key-1")).thenReturn(Optional.empty());
        when(installments.findById(INST)).thenReturn(Optional.of(installment("paid")));

        assertThatThrownBy(() -> svc.initiate(INST, "key-1", "kanmegnea"))
                .isInstanceOf(PaymentNotAllowedException.class);
        verify(gateway, never()).createSepaPaymentIntent(anyString(), anyLong(), anyString());
    }

    @Test
    void returns_503_when_no_gateway_is_wired() {
        allowScreening(); // screening (ALLOW) runs before the gateway-availability check
        when(payments.findByClientKey("key-1")).thenReturn(Optional.empty());
        when(installments.findById(INST)).thenReturn(Optional.of(installment("open")));
        when(invoices.findById(INV)).thenReturn(Optional.of(invoice()));
        when(gatewayProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> svc.initiate(INST, "key-1", "kanmegnea"))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
        verify(payments, never()).save(any());
    }

    @Test
    void unknown_installment_is_a_not_found() {
        when(payments.findByClientKey("key-1")).thenReturn(Optional.empty());
        when(installments.findById(INST)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.initiate(INST, "key-1", "kanmegnea"))
                .isInstanceOf(InstallmentNotFoundException.class);
    }
}
