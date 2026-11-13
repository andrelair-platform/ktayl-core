package com.ktayl.core.complaints.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * L1 — the Complaint aggregate's business rules, exercised as PURE Java: no Spring context, no database,
 * no mocks. This is the hexagonal payoff (ADR-013) — the domain is tested in microseconds and would pass
 * unchanged if JPA/Postgres/REST were all replaced.
 */
class ComplaintTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:15:30Z");

    @Test
    void register_starts_in_REGISTERED_and_keeps_the_facts() {
        Complaint c = Complaint.register(ComplaintChannel.EMAIL, "POL-42", "  premium overcharged  ", "idem-1", NOW);

        assertThat(c.id()).isNotNull();
        assertThat(c.status()).isEqualTo(ComplaintStatus.REGISTERED);
        assertThat(c.channel()).isEqualTo(ComplaintChannel.EMAIL);
        assertThat(c.subjectRef()).isEqualTo("POL-42");
        assertThat(c.summary()).isEqualTo("premium overcharged"); // trimmed
        assertThat(c.registeredAt()).isEqualTo(NOW);
        assertThat(c.idempotencyKey()).isEqualTo("idem-1");
    }

    @Test
    void register_allows_a_null_subject_and_null_idempotency_key() {
        Complaint c = Complaint.register(ComplaintChannel.PHONE, null, "call dropped", null, NOW);
        assertThat(c.subjectRef()).isNull();
        assertThat(c.idempotencyKey()).isNull();
    }

    @Test
    void register_rejects_a_missing_channel() {
        assertThatThrownBy(() -> Complaint.register(null, "POL-1", "x", null, NOW))
                .isInstanceOf(InvalidComplaintException.class)
                .hasMessageContaining("channel");
    }

    @Test
    void register_rejects_a_blank_summary() {
        assertThatThrownBy(() -> Complaint.register(ComplaintChannel.PORTAL, "POL-1", "   ", null, NOW))
                .isInstanceOf(InvalidComplaintException.class)
                .hasMessageContaining("summary");
    }

    @Test
    void registered_event_mirrors_the_aggregate() {
        Complaint c = Complaint.register(ComplaintChannel.BROKER, "POL-7", "mis-sold cover", null, NOW);
        ComplaintRegistered e = c.registeredEvent();

        assertThat(e.complaintId()).isEqualTo(c.id());
        assertThat(e.channel()).isEqualTo(ComplaintChannel.BROKER);
        assertThat(e.registeredAt()).isEqualTo(NOW);
    }
}
