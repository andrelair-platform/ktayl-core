package com.ktayl.core.complaints.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * L1 — the Complaint lifecycle state machine (CMPL-012) as PURE Java: legal + illegal transitions and the
 * acknowledgement-due boundary, with no Spring and no database.
 */
class ComplaintLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:15:30Z");
    private static final Duration WINDOW = Duration.ofDays(2);

    private static Complaint registered() {
        return Complaint.register(ComplaintChannel.EMAIL, "POL-1", "overcharged", null, NOW);
    }

    @Test
    void walks_the_full_legal_lifecycle() {
        Complaint c = registered();
        c.acknowledge(NOW, WINDOW);
        assertThat(c.status()).isEqualTo(ComplaintStatus.ACKNOWLEDGED);
        assertThat(c.acknowledgementDueAt()).isEqualTo(NOW.plus(WINDOW));

        c.assign("adjuster.sophie");
        assertThat(c.status()).isEqualTo(ComplaintStatus.ASSIGNED);
        assertThat(c.assignedTo()).isEqualTo("adjuster.sophie");

        c.resolve("refunded the overcharge");
        assertThat(c.status()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(c.resolutionNote()).isEqualTo("refunded the overcharge");

        c.close();
        assertThat(c.status()).isEqualTo(ComplaintStatus.CLOSED);
    }

    @Test
    void transitions_only_from_their_legal_predecessor() {
        assertThatThrownBy(() -> registered().assign("x")).isInstanceOf(IllegalComplaintTransitionException.class);
        assertThatThrownBy(() -> registered().resolve("x")).isInstanceOf(IllegalComplaintTransitionException.class);
        assertThatThrownBy(() -> registered().close()).isInstanceOf(IllegalComplaintTransitionException.class);

        Complaint acked = registered();
        acked.acknowledge(NOW, WINDOW);
        assertThatThrownBy(() -> acked.acknowledge(NOW, WINDOW)) // double-ack
                .isInstanceOf(IllegalComplaintTransitionException.class);
        assertThatThrownBy(acked::close).isInstanceOf(IllegalComplaintTransitionException.class);
    }

    @Test
    void a_closed_complaint_rejects_every_operation_including_re_close() {
        Complaint c = registered();
        c.acknowledge(NOW, WINDOW);
        c.assign("h");
        c.resolve("done");
        c.close();

        assertThatThrownBy(c::close).isInstanceOf(IllegalComplaintTransitionException.class);      // re-close
        assertThatThrownBy(() -> c.acknowledge(NOW, WINDOW)).isInstanceOf(IllegalComplaintTransitionException.class);
        assertThatThrownBy(() -> c.assign("h2")).isInstanceOf(IllegalComplaintTransitionException.class);
    }

    @Test
    void assign_requires_a_non_blank_handler() {
        Complaint c = registered();
        c.acknowledge(NOW, WINDOW);
        assertThatThrownBy(() -> c.assign("  ")).isInstanceOf(InvalidComplaintException.class);
        assertThatThrownBy(() -> c.assign(null)).isInstanceOf(InvalidComplaintException.class);
        assertThat(c.status()).isEqualTo(ComplaintStatus.ACKNOWLEDGED); // unchanged on failure
    }

    @Test
    void resolve_requires_a_non_blank_note() {
        Complaint c = registered();
        c.acknowledge(NOW, WINDOW);
        c.assign("h");
        assertThatThrownBy(() -> c.resolve("   ")).isInstanceOf(InvalidComplaintException.class);
        assertThat(c.status()).isEqualTo(ComplaintStatus.ASSIGNED); // unchanged on failure
    }

    @Test
    void acknowledgement_overdue_uses_strict_after_the_due_instant() {
        Complaint c = registered();
        assertThat(c.acknowledgementOverdueAt(NOW)).isFalse(); // not acknowledged yet → clock unset

        c.acknowledge(NOW, WINDOW);
        Instant due = NOW.plus(WINDOW);
        assertThat(c.acknowledgementOverdueAt(due.minusNanos(1))).isFalse(); // before due
        assertThat(c.acknowledgementOverdueAt(due)).isFalse();               // exactly due → NOT overdue (< not <=)
        assertThat(c.acknowledgementOverdueAt(due.plusNanos(1))).isTrue();   // after due
    }
}
