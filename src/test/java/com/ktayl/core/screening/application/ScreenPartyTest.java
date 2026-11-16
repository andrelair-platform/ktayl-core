package com.ktayl.core.screening.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktayl.core.screening.domain.Bucket;
import com.ktayl.core.screening.domain.Hit;
import com.ktayl.core.screening.domain.Party;
import com.ktayl.core.screening.domain.ScreeningAuditLog;
import com.ktayl.core.screening.domain.ScreeningEngine;
import com.ktayl.core.screening.domain.ScreeningEngineUnavailableException;
import com.ktayl.core.screening.domain.ScreeningRecord;
import com.ktayl.core.screening.domain.ScreeningResult;
import com.ktayl.core.screening.domain.ScreeningStore;
import com.ktayl.core.screening.domain.Verdict;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * L1 — the ScreenParty use case against mocked ports (no Spring, no DB). Proves the three paths: a clean
 * party → ALLOW + SCREEN audit; a hit → HOLD + alert persisted + SCREEN audit; and the KEY invariant — an
 * engine outage FAILS CLOSED to HOLD (never silent-ALLOW) + a SCREEN_FAILCLOSED audit.
 */
class ScreenPartyTest {

    private final Instant now = Instant.parse("2026-11-16T10:15:30Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ScreeningEngine engine = mock(ScreeningEngine.class);
    private final ScreeningStore store = mock(ScreeningStore.class);
    private final ScreeningAuditLog audit = mock(ScreeningAuditLog.class);
    private final ScreenParty useCase = new ScreenParty(engine, store, audit, clock);

    private final UUID partyId = UUID.randomUUID();
    private final Party input = new Party(null, "EXT-1", "John Doe", null, "FR", "FR", null, null);
    private final Party persisted = new Party(partyId, "EXT-1", "John Doe", null, "FR", "FR", null, null);

    @BeforeEach
    void stubParty() {
        // no existing party for this external ref → it gets persisted, and no prior screening today
        when(store.findPartyByExternalRef("EXT-1")).thenReturn(Optional.empty());
        when(store.saveParty(any(Party.class))).thenReturn(persisted);
        when(store.findScreening(eq(partyId), eq("intake"), any())).thenReturn(Optional.empty());
    }

    @Test
    void clean_party_is_allowed_and_a_screen_audit_is_written() {
        when(engine.match(persisted)).thenReturn(new ScreeningResult("20261116", List.of()));
        when(store.saveScreening(eq(partyId), eq("intake"), any(), eq("20261116"), eq(Verdict.ALLOW), any()))
                .thenReturn(new ScreeningRecord(UUID.randomUUID(), partyId, "intake", now, "20261116",
                        Verdict.ALLOW, List.of(), List.of()));

        ScreenParty.Outcome outcome = useCase.screen(input, "intake", "officer.sophie");

        assertThat(outcome.verdict()).isEqualTo(Verdict.ALLOW);
        assertThat(outcome.alertId()).isNull();
        assertThat(outcome.datasetVersion()).isEqualTo("20261116");
        verify(audit).record(eq(partyId), eq("SCREEN"), eq("officer.sophie"), eq("20261116"), anyString());
    }

    @Test
    void a_hit_holds_the_party_persists_the_alert_and_audits_screen() {
        Hit hit = new Hit(Bucket.SANCTION, 92, "OFAC SDN — John Doe");
        UUID alertId = UUID.randomUUID();
        when(engine.match(persisted)).thenReturn(new ScreeningResult("20261116", List.of(hit)));
        when(store.saveScreening(eq(partyId), eq("intake"), any(), eq("20261116"), eq(Verdict.HOLD), any()))
                .thenReturn(new ScreeningRecord(UUID.randomUUID(), partyId, "intake", now, "20261116",
                        Verdict.HOLD, List.of(alertId), List.of(hit)));

        ScreenParty.Outcome outcome = useCase.screen(input, "intake", "officer.sophie");

        assertThat(outcome.verdict()).isEqualTo(Verdict.HOLD);
        assertThat(outcome.alertId()).isEqualTo(alertId);
        // the alert is persisted via saveScreening(... hits ...) carrying the single hit
        verify(store).saveScreening(eq(partyId), eq("intake"), any(), eq("20261116"), eq(Verdict.HOLD),
                eq(List.of(hit)));
        verify(audit).record(eq(partyId), eq("SCREEN"), eq("officer.sophie"), eq("20261116"), anyString());
    }

    @Test
    void engine_outage_fails_closed_to_hold_with_a_failclosed_audit() {
        when(engine.match(persisted)).thenThrow(new ScreeningEngineUnavailableException("yente unreachable"));
        when(store.saveScreening(eq(partyId), eq("intake"), any(), isNull(), eq(Verdict.HOLD), any()))
                .thenReturn(new ScreeningRecord(UUID.randomUUID(), partyId, "intake", now, null,
                        Verdict.HOLD, List.of(), List.of()));

        ScreenParty.Outcome outcome = useCase.screen(input, "intake", "officer.sophie");

        // NEVER silent-ALLOW when the engine is down — the party is HELD, dataset version is null
        assertThat(outcome.verdict()).isEqualTo(Verdict.HOLD);
        assertThat(outcome.datasetVersion()).isNull();
        verify(store).saveScreening(eq(partyId), eq("intake"), any(), isNull(), eq(Verdict.HOLD), eq(List.of()));
        verify(audit).record(eq(partyId), eq("SCREEN_FAILCLOSED"), eq("officer.sophie"), isNull(), anyString());
        // and no success-path SCREEN audit was written
        verify(audit, never()).record(any(), eq("SCREEN"), any(), any(), any());
    }

    @Test
    void a_repeat_the_same_day_is_idempotent_and_does_not_re_screen() {
        UUID priorAlert = UUID.randomUUID();
        ScreeningRecord prior = new ScreeningRecord(UUID.randomUUID(), partyId, "intake", now, "20261116",
                Verdict.HOLD, List.of(priorAlert), List.of());
        when(store.findScreening(eq(partyId), eq("intake"), any())).thenReturn(Optional.of(prior));

        ScreenParty.Outcome outcome = useCase.screen(input, "intake", "officer.sophie");

        assertThat(outcome.verdict()).isEqualTo(Verdict.HOLD);
        assertThat(outcome.alertId()).isEqualTo(priorAlert);
        verify(engine, never()).match(any());        // no second engine call
        verify(store, never()).saveScreening(any(), any(), any(), any(), any(), any());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }
}
