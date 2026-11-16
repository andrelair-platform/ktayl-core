package com.ktayl.core.policy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktayl.core.policy.domain.Policy;
import com.ktayl.core.policy.domain.PolicyRepository;
import com.ktayl.core.policy.domain.PolicyStatus;
import com.ktayl.core.screening.api.PartyRef;
import com.ktayl.core.screening.api.ScreeningGate;
import com.ktayl.core.screening.api.ScreeningOutcome;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * L1 — the bind (SUBMITTED → ACTIVE) screening gate (RC-02 S2). A HOLD verdict must block the bind
 * (exception thrown, policy NOT activated/saved); an ALLOW proceeds exactly as before. The screening gate,
 * repository and audit log are mocked — the real-engine proof is the L2/L3 tests.
 */
class PolicyServiceTest {

    private final PolicyRepository policies = mock(PolicyRepository.class);
    private final PolicyAuditLog audit = mock(PolicyAuditLog.class);
    private final ScreeningGate screening = mock(ScreeningGate.class);

    private final PolicyService svc = new PolicyService(policies, audit, screening);

    /** A SUBMITTED policy (bind precondition), built through the aggregate's own transitions. */
    private static Policy submittedPolicy() {
        Policy p = Policy.create("POL-1", "Acme Marine SA", "MARINE",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), Instant.now());
        p.submit(Instant.now());
        return p;
    }

    @Test
    void allow_activates_and_saves_as_before() {
        Policy policy = submittedPolicy();
        when(policies.findById(policy.id())).thenReturn(Optional.of(policy));
        when(screening.screen(any(PartyRef.class), anyString(), anyString()))
                .thenReturn(new ScreeningOutcome("ALLOW", null, "v-2026-11-16"));

        Policy result = svc.activate(policy.id(), "kanmegnea");

        assertThat(result.status()).isEqualTo(PolicyStatus.ACTIVE);
        // screened the holder at the bind gate with the acting principal
        verify(screening).screen(
                argThatParty("POL-1", "Acme Marine SA"), eq("bind"), eq("kanmegnea"));
        verify(policies).save(policy);
        verify(audit).record(eq(policy.id()), eq("ACTIVATE"), eq(PolicyStatus.SUBMITTED),
                eq(PolicyStatus.ACTIVE), eq("kanmegnea"), eq(null));
    }

    @Test
    void hold_blocks_the_bind_and_leaves_the_policy_submitted_unsaved() {
        Policy policy = submittedPolicy();
        UUID alert = UUID.randomUUID();
        when(policies.findById(policy.id())).thenReturn(Optional.of(policy));
        when(screening.screen(any(PartyRef.class), anyString(), anyString()))
                .thenReturn(new ScreeningOutcome("HOLD", alert, null));

        assertThatThrownBy(() -> svc.activate(policy.id(), "kanmegnea"))
                .isInstanceOf(BindScreeningHoldException.class)
                .extracting("policyId", "alertId", "gate")
                .containsExactly(policy.id(), alert, "bind");

        assertThat(policy.status()).isEqualTo(PolicyStatus.SUBMITTED); // not activated
        verify(policies, never()).save(any());
        verify(audit, never()).record(any(), anyString(), any(), any(), anyString(), any());
    }

    private static PartyRef argThatParty(String externalRef, String name) {
        return org.mockito.ArgumentMatchers.argThat(p ->
                externalRef.equals(p.externalRef()) && name.equals(p.name()));
    }
}
