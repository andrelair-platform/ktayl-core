package com.ktayl.core.finance.infrastructure.reserving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktayl.core.finance.api.IbnrEstimateView;
import com.ktayl.core.finance.api.ReserveReviewRequest;
import com.ktayl.core.finance.api.ReserveReviewView;
import com.ktayl.core.finance.api.ReservingOperations;
import com.ktayl.core.finance.api.TriangleLoadRequest;
import com.ktayl.core.finance.api.TriangleLoadRequest.Cell;
import com.ktayl.core.finance.domain.reserving.SelfApprovalException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * L2 — FIN-03 actuarial reserving over a REAL Postgres: load a loss triangle, estimate IBNR (chain-ladder &
 * Bornhuetter-Ferguson), run it through the maker-checker review, and prove the approved IBNR movement lands on
 * the GL outbox (signed, delta vs the last approved position). Also proves the segregation-of-duties control
 * bites live (a maker cannot approve their own review). The ledger client is unconfigured, so the outbox
 * accumulates (never posts) — exactly the dev posture. The triangle is the clean textbook one from the L1 tests
 * (factors 2.0/1.5/1.25 → total IBNR 775).
 */
@SpringBootTest(properties = {"ledger.drain-initial-ms=3600000", "ledger.drain-interval-ms=3600000"})
@Testcontainers
class ReservingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired ReservingOperations reserving;
    @Autowired ReserveMovementOutboxRepository outbox;

    private static final LocalDate AS_OF = LocalDate.parse("2026-09-30");

    private void loadCleanTriangle(String portfolio) {
        reserving.loadTriangle(new TriangleLoadRequest(portfolio, AS_OF, "INCURRED", "EUR", List.of(
                new Cell("2021-A", 0, 100), new Cell("2021-A", 1, 200), new Cell("2021-A", 2, 300), new Cell("2021-A", 3, 375),
                new Cell("2021-B", 0, 120), new Cell("2021-B", 1, 240), new Cell("2021-B", 2, 360),
                new Cell("2021-C", 0, 140), new Cell("2021-C", 1, 280),
                new Cell("2021-D", 0, 160))), "olivia.maker");
    }

    @Test
    void chain_ladder_estimate_matches_the_hand_computed_ibnr() {
        loadCleanTriangle("marine-a");
        IbnrEstimateView est = reserving.estimate("marine-a", AS_OF, "INCURRED", "CHAIN_LADDER", null);
        assertThat(est.totalLatestMinor()).isEqualTo(1175L);
        assertThat(est.totalUltimateMinor()).isEqualTo(1950L);
        assertThat(est.totalIbnrMinor()).isEqualTo(775L);
    }

    @Test
    void approved_review_books_the_full_ibnr_movement_to_the_gl_outbox() {
        loadCleanTriangle("marine-b");
        ReserveReviewView submitted = reserving.submitReview(
                new ReserveReviewRequest("marine-b", AS_OF, "INCURRED", "CHAIN_LADDER", null), "olivia.maker");
        assertThat(submitted.state()).isEqualTo("PENDING");
        assertThat(submitted.estimatedIbnrMinor()).isEqualTo(775L);

        ReserveReviewView approved = reserving.approve(submitted.id(), "signed off", "marcus.checker");
        assertThat(approved.state()).isEqualTo("APPROVED");
        assertThat(approved.checker()).isEqualTo("marcus.checker");
        assertThat(approved.bookedMovementMinor()).isEqualTo(775L); // no prior position → full IBNR

        var rows = outbox.findByStatusOrderByCreatedAt("pending", org.springframework.data.domain.Limit.of(10))
                .stream().filter(r -> r.getPortfolio().equals("marine-b")).toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAmountMinor()).isEqualTo(775L);
        assertThat(rows.get(0).getReference()).isEqualTo(submitted.id().toString());
    }

    @Test
    void the_maker_cannot_approve_their_own_review() {
        loadCleanTriangle("marine-c");
        ReserveReviewView submitted = reserving.submitReview(
                new ReserveReviewRequest("marine-c", AS_OF, "INCURRED", "CHAIN_LADDER", null), "olivia.maker");
        assertThatThrownBy(() -> reserving.approve(submitted.id(), null, "olivia.maker"))
                .isInstanceOf(SelfApprovalException.class);
    }

    @Test
    void a_second_approval_books_only_the_delta_vs_the_prior_position() {
        loadCleanTriangle("marine-d");
        // first position: chain-ladder IBNR 775, booked in full
        ReserveReviewView cl = reserving.submitReview(
                new ReserveReviewRequest("marine-d", AS_OF, "INCURRED", "CHAIN_LADDER", null), "olivia.maker");
        reserving.approve(cl.id(), null, "marcus.checker");

        // second review, Bornhuetter-Ferguson with a richer a-priori on the green year → IBNR 922
        ReserveReviewView bf = reserving.submitReview(new ReserveReviewRequest("marine-d", AS_OF, "INCURRED",
                "BORNHUETTER_FERGUSON", Map.of("2021-D", 800L)), "olivia.maker");
        assertThat(bf.estimatedIbnrMinor()).isEqualTo(922L);
        ReserveReviewView bfApproved = reserving.approve(bf.id(), null, "marcus.checker");

        // the movement booked is the delta 922 − 775 = 147, not the whole new position
        assertThat(bfApproved.bookedMovementMinor()).isEqualTo(147L);
        var delta = outbox.findByStatusOrderByCreatedAt("pending", org.springframework.data.domain.Limit.of(50))
                .stream().filter(r -> r.getReference().equals(bf.id().toString())).toList();
        assertThat(delta).hasSize(1);
        assertThat(delta.get(0).getAmountMinor()).isEqualTo(147L);
    }
}
