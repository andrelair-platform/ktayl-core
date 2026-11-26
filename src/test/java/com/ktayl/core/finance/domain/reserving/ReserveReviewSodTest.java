package com.ktayl.core.finance.domain.reserving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktayl.core.finance.infrastructure.persistence.ReserveReviewEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * L1 — the maker-checker / segregation-of-duties control on a reserve review (FIN-03, threat T7). The entity is
 * a plain POJO here (no Spring, no DB), so the control is proven as a pure invariant: a checker who is the maker
 * is refused; a terminal (decided) review cannot be decided again; a genuine four-eyes approval/rejection goes
 * through and records the checker + movement.
 */
class ReserveReviewSodTest {

    private static ReserveReviewEntity pending(String maker) {
        return new ReserveReviewEntity(UUID.randomUUID(), "marine", LocalDate.parse("2026-09-30"), "INCURRED",
                "CHAIN_LADDER", "EUR", 1_175_00L, 1_950_00L, 775_00L, "{}", maker, Instant.now());
    }

    @Test
    void the_maker_cannot_approve_their_own_review() {
        ReserveReviewEntity review = pending("olivia.maker");
        assertThatThrownBy(() -> review.approve("olivia.maker", 775_00L, null, Instant.now()))
                .isInstanceOf(SelfApprovalException.class);
        assertThat(review.getState()).isEqualTo(ReserveReviewState.PENDING); // unchanged
    }

    @Test
    void the_maker_cannot_reject_their_own_review_even_case_insensitively() {
        ReserveReviewEntity review = pending("Olivia.Maker");
        assertThatThrownBy(() -> review.reject("olivia.maker", "nope", Instant.now()))
                .isInstanceOf(SelfApprovalException.class);
    }

    @Test
    void an_independent_checker_approves_and_the_movement_is_recorded() {
        ReserveReviewEntity review = pending("olivia.maker");
        Instant at = Instant.parse("2026-10-01T09:00:00Z");
        review.approve("marcus.checker", 775_00L, "signed off", at);

        assertThat(review.getState()).isEqualTo(ReserveReviewState.APPROVED);
        assertThat(review.getChecker()).isEqualTo("marcus.checker");
        assertThat(review.getBookedMovementMinor()).isEqualTo(775_00L);
        assertThat(review.getDecidedAt()).isEqualTo(at);
    }

    @Test
    void an_already_approved_review_cannot_be_decided_again() {
        ReserveReviewEntity review = pending("olivia.maker");
        review.approve("marcus.checker", 775_00L, null, Instant.now());
        assertThatThrownBy(() -> review.reject("someone.else", "too late", Instant.now()))
                .isInstanceOf(ReserveReviewNotPendingException.class);
    }

    @Test
    void an_independent_checker_rejects_with_a_reason() {
        ReserveReviewEntity review = pending("olivia.maker");
        review.reject("marcus.checker", "a-priori looks too optimistic", Instant.now());
        assertThat(review.getState()).isEqualTo(ReserveReviewState.REJECTED);
        assertThat(review.getBookedMovementMinor()).isNull(); // nothing booked on a rejection
    }
}
