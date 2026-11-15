package com.ktayl.core.policy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** L1 — the policy lifecycle state machine + creation invariants (pure domain, no Spring/DB). */
class PolicyTest {

    private static final Instant NOW = Instant.now();

    private Policy draft() {
        return Policy.create("POL-1", "ACME SA", "MARINE", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), NOW);
    }

    @Test
    void create_starts_in_draft_and_enforces_invariants() {
        assertThat(draft().status()).isEqualTo(PolicyStatus.DRAFT);
        assertThatThrownBy(() -> Policy.create("", "h", "p", LocalDate.now(), LocalDate.now().plusDays(1), NOW))
                .isInstanceOf(InvalidPolicyException.class);
        assertThatThrownBy(() -> Policy.create("P", "h", "p", LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1), NOW))
                .isInstanceOf(InvalidPolicyException.class).hasMessageContaining("before");
    }

    @Test
    void bind_path_draft_to_submitted_to_active() {
        Policy p = draft();
        p.submit(NOW);
        assertThat(p.status()).isEqualTo(PolicyStatus.SUBMITTED);
        p.activate(NOW);
        assertThat(p.status()).isEqualTo(PolicyStatus.ACTIVE);
    }

    @Test
    void transitions_run_only_from_their_legal_predecessor() {
        assertThatThrownBy(() -> draft().activate(NOW)).isInstanceOf(IllegalPolicyTransitionException.class); // DRAFT can't activate
        Policy p = draft();
        p.submit(NOW);
        assertThatThrownBy(() -> p.submit(NOW)).isInstanceOf(IllegalPolicyTransitionException.class); // double submit
    }

    @Test
    void submitted_can_be_rejected() {
        Policy p = draft();
        p.submit(NOW);
        p.reject(NOW);
        assertThat(p.status()).isEqualTo(PolicyStatus.REJECTED);
    }

    @Test
    void active_can_amend_confirm_cancel_expire() {
        Policy p = active();
        p.amend(NOW);
        assertThat(p.status()).isEqualTo(PolicyStatus.AMENDED);
        p.confirmAmendment(NOW);
        assertThat(p.status()).isEqualTo(PolicyStatus.ACTIVE);

        Policy c = active();
        c.cancel("NON_PAYMENT", NOW);
        assertThat(c.status()).isEqualTo(PolicyStatus.CANCELLED);
    }

    @Test
    void cancel_requires_a_reason() {
        assertThatThrownBy(() -> active().cancel("  ", NOW)).isInstanceOf(InvalidPolicyException.class);
    }

    @Test
    void update_changes_details_only_while_draft() {
        Policy p = draft();
        p.update("NEW HOLDER", "AVIATION", LocalDate.of(2026, 2, 1), LocalDate.of(2027, 2, 1), NOW);
        assertThat(p.holderName()).isEqualTo("NEW HOLDER");
        assertThat(p.productCode()).isEqualTo("AVIATION");
        assertThat(p.effectiveDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(p.status()).isEqualTo(PolicyStatus.DRAFT);
    }

    @Test
    void update_is_rejected_once_past_draft() {
        Policy p = draft();
        p.submit(NOW);
        assertThatThrownBy(() -> p.update("x", "y", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), NOW))
                .isInstanceOf(IllegalPolicyTransitionException.class);
    }

    @Test
    void update_revalidates_fields() {
        assertThatThrownBy(() -> draft().update(" ", "p", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), NOW))
                .isInstanceOf(InvalidPolicyException.class);
        assertThatThrownBy(() -> draft().update("h", "p", LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1), NOW))
                .isInstanceOf(InvalidPolicyException.class).hasMessageContaining("before");
    }

    private Policy active() {
        Policy p = draft();
        p.submit(NOW);
        p.activate(NOW);
        return p;
    }
}
