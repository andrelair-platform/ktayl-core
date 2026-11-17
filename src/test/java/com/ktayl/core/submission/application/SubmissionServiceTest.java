package com.ktayl.core.submission.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktayl.core.screening.api.PartyRef;
import com.ktayl.core.screening.api.ScreeningGate;
import com.ktayl.core.screening.api.ScreeningOutcome;
import com.ktayl.core.submission.api.RegisterSubmissionCommand;
import com.ktayl.core.submission.api.RouteSubmissionCommand;
import com.ktayl.core.submission.api.TriageSubmissionCommand;
import com.ktayl.core.submission.domain.ExposureItem;
import com.ktayl.core.submission.domain.InvalidSubmissionException;
import com.ktayl.core.submission.domain.Submission;
import com.ktayl.core.submission.domain.SubmissionRepository;
import com.ktayl.core.submission.domain.SubmissionScreeningHoldException;
import com.ktayl.core.submission.domain.SubmissionStatus;
import com.ktayl.core.submission.domain.TriageDecision;
import com.ktayl.core.submission.domain.TriageLog;
import com.ktayl.core.submission.domain.UnderwritingHandoffException;
import com.ktayl.core.submission.domain.UnderwritingPort;
import com.ktayl.core.submission.domain.UnderwritingSubmissionRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * L1 — the SubmissionService use cases against mocked ports (no Spring, no DB). Proves the KEY intake
 * invariant — register SCREENS the insured and flags a HOLD when the screening gate returns HOLD, and does
 * NOT flag on ALLOW — plus the triage/route/decline orchestration and the route-blocked-under-hold guard.
 * The real-engine proof is the L2 integration test.
 */
class SubmissionServiceTest {

    private final Instant now = Instant.parse("2026-11-17T10:15:30Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final SubmissionRepository repo = mock(SubmissionRepository.class);
    private final TriageLog triageLog = mock(TriageLog.class);
    private final ScreeningGate screening = mock(ScreeningGate.class);
    private final UnderwritingPort underwriting = mock(UnderwritingPort.class);
    private final SubmissionService svc =
            new SubmissionService(repo, triageLog, screening, underwriting, clock);

    private final RegisterSubmissionCommand cmd =
            new RegisterSubmissionCommand("BRK-1", "ACME SA", "MARINE");

    @Test
    void register_screens_the_insured_at_intake_and_does_not_hold_on_allow() {
        when(screening.screen(any(PartyRef.class), anyString(), anyString()))
                .thenReturn(new ScreeningOutcome("ALLOW", null, "20261117"));
        when(repo.save(any(Submission.class))).thenAnswer(i -> i.getArgument(0));

        svc.register(cmd, "agent.kanmegnea");

        // screened the insured at the intake gate with the acting principal
        verify(screening).screen(argThatParty("ACME SA"), eq("intake"), eq("agent.kanmegnea"));
        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().screeningHold()).isFalse();
        assertThat(saved.getValue().status()).isEqualTo(SubmissionStatus.RECEIVED);
    }

    @Test
    void register_flags_the_hold_when_screening_returns_HOLD_but_still_captures() {
        UUID alert = UUID.randomUUID();
        when(screening.screen(any(PartyRef.class), anyString(), anyString()))
                .thenReturn(new ScreeningOutcome("HOLD", alert, null));
        when(repo.save(any(Submission.class))).thenAnswer(i -> i.getArgument(0));

        svc.register(cmd, "agent.kanmegnea");

        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(repo).save(saved.capture());
        // still captured (RECEIVED), but flagged + the alert recorded — HITL will clear it
        assertThat(saved.getValue().status()).isEqualTo(SubmissionStatus.RECEIVED);
        assertThat(saved.getValue().screeningHold()).isTrue();
        assertThat(saved.getValue().screeningAlertId()).isEqualTo(alert);
    }

    @Test
    void triage_persists_the_transition_and_records_an_append_only_triage_row() {
        Submission s = Submission.receive("BRK-1", "ACME SA", "MARINE", now);
        when(repo.findById(s.id())).thenReturn(Optional.of(s));
        when(repo.save(any(Submission.class))).thenAnswer(i -> i.getArgument(0));

        svc.triage(s.id(), new TriageSubmissionCommand("route", 80, "IN_APPETITE", "complete dossier"),
                "agent.kanmegnea");

        assertThat(s.status()).isEqualTo(SubmissionStatus.TRIAGED);
        verify(triageLog).record(eq(s.id()), eq(80), eq("IN_APPETITE"), eq(TriageDecision.ROUTE),
                eq("complete dossier"), eq("agent.kanmegnea"));
    }

    @Test
    void triage_rejects_an_unknown_decision_before_any_write() {
        Submission s = Submission.receive("BRK-1", "ACME SA", "MARINE", now);
        when(repo.findById(s.id())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> svc.triage(s.id(), new TriageSubmissionCommand("NOPE", null, null, null),
                "agent.kanmegnea")).isInstanceOf(InvalidSubmissionException.class);
        verify(repo, never()).save(any());
        verify(triageLog, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void route_is_refused_when_the_submission_is_under_a_hold() {
        Submission s = Submission.receive("BRK-1", "ACME SA", "MARINE", now);
        s.flagScreeningHold(UUID.randomUUID());
        s.triage(TriageDecision.ROUTE, now);
        when(repo.findById(s.id())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> svc.route(s.id(), new RouteSubmissionCommand("underwriting"),
                "agent.kanmegnea")).isInstanceOf(SubmissionScreeningHoldException.class);
        verify(repo, never()).save(any()); // nothing routed/persisted
        verify(underwriting, never()).createSubmission(any()); // and no handoff
    }

    @Test
    void clear_hold_then_route_persists_routed() {
        Submission s = Submission.receive("BRK-1", "ACME SA", "MARINE", now);
        s.flagScreeningHold(UUID.randomUUID());
        s.triage(TriageDecision.ROUTE, now);
        when(repo.findById(s.id())).thenReturn(Optional.of(s));
        when(repo.save(any(Submission.class))).thenAnswer(i -> i.getArgument(0));
        when(repo.findExposures(s.id())).thenReturn(List.of());
        when(underwriting.createSubmission(any())).thenReturn(UUID.randomUUID().toString());

        svc.clearScreeningHold(s.id(), "officer.sophie");
        svc.route(s.id(), new RouteSubmissionCommand("underwriting"), "agent.kanmegnea");

        assertThat(s.status()).isEqualTo(SubmissionStatus.ROUTED);
        assertThat(s.routedTarget()).isEqualTo("underwriting");
    }

    @Test
    void route_hands_off_to_underwriting_with_the_mapped_request_and_records_the_uw_ref() {
        Submission s = Submission.receive("BRK-1", "ACME SA", "MARINE", now);
        s.triage(TriageDecision.ROUTE, now);
        ExposureItem exposure = ExposureItem.create(s.id(), "Rotterdam Terminal", "NL",
                new BigDecimal("12500000.00"), "EUR", "warehouse", "3011AA", "no losses", true, now);
        when(repo.findById(s.id())).thenReturn(Optional.of(s));
        when(repo.save(any(Submission.class))).thenAnswer(i -> i.getArgument(0));
        when(repo.findExposures(s.id())).thenReturn(List.of(exposure));
        String uwRef = UUID.randomUUID().toString();
        when(underwriting.createSubmission(any())).thenReturn(uwRef);

        svc.route(s.id(), new RouteSubmissionCommand("underwriting"), "agent.kanmegnea");

        ArgumentCaptor<UnderwritingSubmissionRequest> req =
                ArgumentCaptor.forClass(UnderwritingSubmissionRequest.class);
        verify(underwriting).createSubmission(req.capture());
        // the dossier was mapped onto the UW contract
        assertThat(req.getValue().counterpartyName()).isEqualTo("ACME SA");
        assertThat(req.getValue().lineOfBusiness()).isEqualTo("marine");
        assertThat(req.getValue().country()).isEqualTo("NL");
        assertThat(req.getValue().postcode()).isEqualTo("3011AA");
        assertThat(req.getValue().occupancy()).isEqualTo("warehouse");
        assertThat(req.getValue().tivEurCents()).isEqualTo(1_250_000_000L); // 12,500,000.00 EUR → eurocents
        // the UW submission id is recorded on the aggregate
        assertThat(s.status()).isEqualTo(SubmissionStatus.ROUTED);
        assertThat(s.uwSubmissionRef()).isEqualTo(uwRef);
    }

    @Test
    void a_failed_handoff_surfaces_and_leaves_the_submission_routed_with_no_uw_ref() {
        Submission s = Submission.receive("BRK-1", "ACME SA", "MARINE", now);
        s.triage(TriageDecision.ROUTE, now);
        when(repo.findById(s.id())).thenReturn(Optional.of(s));
        when(repo.save(any(Submission.class))).thenAnswer(i -> i.getArgument(0));
        when(repo.findExposures(s.id())).thenReturn(List.of());
        when(underwriting.createSubmission(any()))
                .thenThrow(new UnderwritingHandoffException(s.id(), "connection refused", null));

        assertThatThrownBy(() -> svc.route(s.id(), new RouteSubmissionCommand("underwriting"),
                "agent.kanmegnea")).isInstanceOf(UnderwritingHandoffException.class);

        // routed locally (the transition + save already happened), but no UW ref recorded → retryable
        assertThat(s.status()).isEqualTo(SubmissionStatus.ROUTED);
        assertThat(s.uwSubmissionRef()).isNull();
    }

    private static PartyRef argThatParty(String name) {
        return org.mockito.ArgumentMatchers.argThat(p -> p != null && name.equals(p.name()));
    }
}
