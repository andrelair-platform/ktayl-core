package com.ktayl.core.screening.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktayl.core.screening.domain.AlertNotFoundException;
import com.ktayl.core.screening.domain.AlertView;
import com.ktayl.core.screening.domain.Bucket;
import com.ktayl.core.screening.domain.CaseDecision;
import com.ktayl.core.screening.domain.CaseStatus;
import com.ktayl.core.screening.domain.Risk;
import com.ktayl.core.screening.domain.ScreeningAuditLog;
import com.ktayl.core.screening.domain.ScreeningStore;
import com.ktayl.core.screening.domain.Vigilance;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * L1 — the DecideAlert HITL use case against mocked ports. A decision persists the case + a DECIDE audit; an
 * unknown alert raises AlertNotFound (→404) and writes nothing.
 */
class DecideAlertTest {

    private final Instant now = Instant.parse("2026-11-16T10:15:30Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ScreeningStore store = mock(ScreeningStore.class);
    private final ScreeningAuditLog audit = mock(ScreeningAuditLog.class);
    private final DecideAlert useCase = new DecideAlert(store, audit, clock);

    private final UUID alertId = UUID.randomUUID();
    private final UUID partyId = UUID.randomUUID();

    @Test
    void persists_the_case_decision_and_audits() {
        when(store.findAlert(alertId)).thenReturn(Optional.of(new AlertView(alertId, UUID.randomUUID(),
                partyId, Bucket.SANCTION, 92, "OFAC SDN", null)));

        useCase.decide(alertId, CaseStatus.REF, Risk.STRONG, Vigilance.ENHANCED, "confirmed sanctions match",
                "officer.sophie");

        ArgumentCaptor<CaseDecision> saved = ArgumentCaptor.forClass(CaseDecision.class);
        verify(store).saveCase(saved.capture());
        assertThat(saved.getValue().alertId()).isEqualTo(alertId);
        assertThat(saved.getValue().status()).isEqualTo(CaseStatus.REF);
        assertThat(saved.getValue().risk()).isEqualTo(Risk.STRONG);
        assertThat(saved.getValue().vigilance()).isEqualTo(Vigilance.ENHANCED);
        assertThat(saved.getValue().actorId()).isEqualTo("officer.sophie");
        assertThat(saved.getValue().decidedAt()).isEqualTo(now);
        verify(audit).record(eq(partyId), eq("DECIDE"), eq("officer.sophie"), any(), any());
    }

    @Test
    void an_unknown_alert_is_rejected_and_nothing_is_written() {
        when(store.findAlert(alertId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.decide(alertId, CaseStatus.ACC, Risk.WEAK, Vigilance.NORMAL, "ok",
                "officer.sophie"))
                .isInstanceOf(AlertNotFoundException.class);

        verify(store, never()).saveCase(any());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }
}
