package com.ktayl.core.billing.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktayl.core.billing.ingest.PolicyIngestService.Outcome;
import com.ktayl.core.billing.persistence.AuditRepository;
import com.ktayl.core.billing.persistence.IngestedPolicyRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** L1 — ingest logic: idempotency, happy-path persistence+audit+event, and poison rejection. */
class PolicyIngestServiceTest {

    private final IngestedPolicyRepository policies = mock(IngestedPolicyRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PolicyIngestService svc = new PolicyIngestService(policies, audit, events);

    private static final LocalDate D1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate D2 = LocalDate.of(2027, 1, 1);

    private static BoundRiskEvent valid() {
        return new BoundRiskEvent("POL-1", 120_000L, "EUR", "PROPERTY", D1, D2, "SUB-1", "Q-1");
    }

    @Test
    void ingests_a_new_bound_policy_and_audits_it() {
        when(policies.existsById("POL-1")).thenReturn(false);
        assertThat(svc.ingest(valid())).isEqualTo(Outcome.INGESTED);
        verify(policies).save(any());
        verify(audit).save(any());
        verify(events).publishEvent(any(PolicyIngested.class)); // auto-chain to invoicing
    }

    @Test
    void is_idempotent_on_a_duplicate_policy_number() {
        when(policies.existsById("POL-1")).thenReturn(true);
        assertThat(svc.ingest(valid())).isEqualTo(Outcome.SKIPPED_DUPLICATE);
        verify(policies, never()).save(any());
        verify(audit, never()).save(any());
        verify(events, never()).publishEvent(any()); // no re-chain on a duplicate
    }

    @Test
    void rejects_poison_events() {
        assertThatThrownBy(() -> svc.ingest(new BoundRiskEvent(" ", 1, "EUR", "P", D1, D2, null, null)))
                .isInstanceOf(IllegalArgumentException.class); // blank policy_number
        assertThatThrownBy(() -> svc.ingest(new BoundRiskEvent("P", 0, "EUR", "P", D1, D2, null, null)))
                .isInstanceOf(IllegalArgumentException.class); // premium_minor must be > 0
        assertThatThrownBy(() -> svc.ingest(new BoundRiskEvent("P", 1, "EURO", "P", D1, D2, null, null)))
                .isInstanceOf(IllegalArgumentException.class); // currency must be ISO-4217 (3 chars)
        assertThatThrownBy(() -> svc.ingest(new BoundRiskEvent("P", 1, "EUR", "P", null, D2, null, null)))
                .isInstanceOf(IllegalArgumentException.class); // dates required
    }
}
