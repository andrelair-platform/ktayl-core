package com.ktayl.core.submission.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * L1 — the Submission aggregate's state machine + screening-hold rules, exercised as PURE Java: no Spring
 * context, no database, no mocks. This is the hexagonal payoff (ADR-013) — the domain is tested in
 * microseconds and would pass unchanged if JPA/Postgres/REST were all replaced.
 */
class SubmissionTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:15:30Z");

    private static Submission received() {
        return Submission.receive("BRK-1", "  ACME SA  ", "MARINE", NOW);
    }

    // ── receive ──────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void receive_starts_in_RECEIVED_not_held_and_trims() {
        Submission s = received();
        assertThat(s.id()).isNotNull();
        assertThat(s.status()).isEqualTo(SubmissionStatus.RECEIVED);
        assertThat(s.brokerRef()).isEqualTo("BRK-1");
        assertThat(s.insuredName()).isEqualTo("ACME SA"); // trimmed
        assertThat(s.productLine()).isEqualTo("MARINE");
        assertThat(s.screeningHold()).isFalse();
        assertThat(s.routedTarget()).isNull();
        assertThat(s.receivedAt()).isEqualTo(NOW);
    }

    @Test
    void receive_rejects_blank_required_fields() {
        assertThatThrownBy(() -> Submission.receive("  ", "ACME", "MARINE", NOW))
                .isInstanceOf(InvalidSubmissionException.class).hasMessageContaining("broker ref");
        assertThatThrownBy(() -> Submission.receive("BRK", "  ", "MARINE", NOW))
                .isInstanceOf(InvalidSubmissionException.class).hasMessageContaining("insured name");
        assertThatThrownBy(() -> Submission.receive("BRK", "ACME", null, NOW))
                .isInstanceOf(InvalidSubmissionException.class).hasMessageContaining("product line");
    }

    // ── triage ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void triage_ROUTE_moves_received_to_triaged() {
        Submission s = received();
        s.triage(TriageDecision.ROUTE, NOW);
        assertThat(s.status()).isEqualTo(SubmissionStatus.TRIAGED);
    }

    @Test
    void triage_DECLINE_moves_received_to_declined() {
        Submission s = received();
        s.triage(TriageDecision.DECLINE, NOW);
        assertThat(s.status()).isEqualTo(SubmissionStatus.DECLINED);
    }

    @Test
    void triage_RETURN_keeps_received() {
        Submission s = received();
        s.triage(TriageDecision.RETURN, NOW);
        assertThat(s.status()).isEqualTo(SubmissionStatus.RECEIVED);
    }

    @Test
    void triage_requires_a_decision() {
        assertThatThrownBy(() -> received().triage(null, NOW))
                .isInstanceOf(InvalidSubmissionException.class);
    }

    @Test
    void triage_from_a_non_received_state_is_illegal() {
        Submission s = received();
        s.triage(TriageDecision.ROUTE, NOW); // now TRIAGED
        assertThatThrownBy(() -> s.triage(TriageDecision.ROUTE, NOW))
                .isInstanceOf(IllegalSubmissionTransitionException.class);
    }

    // ── route ────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void route_moves_triaged_to_routed_with_a_target() {
        Submission s = received();
        s.triage(TriageDecision.ROUTE, NOW);
        s.route("underwriting", NOW);
        assertThat(s.status()).isEqualTo(SubmissionStatus.ROUTED);
        assertThat(s.routedTarget()).isEqualTo("underwriting");
    }

    @Test
    void route_before_triage_is_illegal() {
        assertThatThrownBy(() -> received().route("underwriting", NOW))
                .isInstanceOf(IllegalSubmissionTransitionException.class);
    }

    @Test
    void route_requires_a_target() {
        Submission s = received();
        s.triage(TriageDecision.ROUTE, NOW);
        assertThatThrownBy(() -> s.route("  ", NOW)).isInstanceOf(InvalidSubmissionException.class);
    }

    @Test
    void route_is_refused_while_under_a_screening_hold() {
        Submission s = received();
        UUID alert = UUID.randomUUID();
        s.flagScreeningHold(alert);
        s.triage(TriageDecision.ROUTE, NOW); // triage is still allowed while held
        assertThatThrownBy(() -> s.route("underwriting", NOW))
                .isInstanceOf(SubmissionScreeningHoldException.class)
                .satisfies(e -> assertThat(((SubmissionScreeningHoldException) e).alertId()).isEqualTo(alert));
        assertThat(s.status()).isEqualTo(SubmissionStatus.TRIAGED); // not routed
    }

    @Test
    void clearing_the_hold_lets_a_triaged_submission_route() {
        Submission s = received();
        s.flagScreeningHold(UUID.randomUUID());
        s.triage(TriageDecision.ROUTE, NOW);
        s.clearScreeningHold(NOW);
        s.route("underwriting", NOW);
        assertThat(s.status()).isEqualTo(SubmissionStatus.ROUTED);
        assertThat(s.screeningHold()).isFalse();
    }

    @Test
    void flagging_the_hold_keeps_the_first_alert_reference() {
        Submission s = received();
        UUID first = UUID.randomUUID();
        s.flagScreeningHold(first);
        s.flagScreeningHold(UUID.randomUUID());
        assertThat(s.screeningAlertId()).isEqualTo(first);
        assertThat(s.screeningHold()).isTrue();
    }

    // ── decline ──────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void decline_from_received_or_triaged_is_terminal() {
        Submission a = received();
        a.decline("off appetite", NOW);
        assertThat(a.status()).isEqualTo(SubmissionStatus.DECLINED);

        Submission b = received();
        b.triage(TriageDecision.ROUTE, NOW);
        b.decline("withdrawn by broker", NOW);
        assertThat(b.status()).isEqualTo(SubmissionStatus.DECLINED);
    }

    @Test
    void decline_requires_a_reason() {
        assertThatThrownBy(() -> received().decline("  ", NOW))
                .isInstanceOf(InvalidSubmissionException.class);
    }

    @Test
    void decline_of_a_routed_submission_is_illegal() {
        Submission s = received();
        s.triage(TriageDecision.ROUTE, NOW);
        s.route("underwriting", NOW);
        assertThatThrownBy(() -> s.decline("too late", NOW))
                .isInstanceOf(IllegalSubmissionTransitionException.class);
    }
}
