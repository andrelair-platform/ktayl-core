package com.ktayl.core.complaints.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktayl.core.complaints.domain.Complaint;
import com.ktayl.core.complaints.domain.ComplaintChannel;
import com.ktayl.core.complaints.domain.ComplaintNotFoundException;
import com.ktayl.core.complaints.domain.ComplaintRepository;
import com.ktayl.core.complaints.domain.ComplaintStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * L1 — the ComplaintLifecycle use cases against mocked ports (no Spring, no DB): load → mutate → persist →
 * audit each transition, and the not-found path.
 */
class ComplaintLifecycleTest {

    private final Instant now = Instant.parse("2026-11-13T10:15:30Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private final AuditTrail audit = mock(AuditTrail.class);
    private final ComplaintLifecycle lifecycle =
            new ComplaintLifecycle(complaints, audit, clock, Duration.ofDays(2));

    private Complaint acknowledged() {
        Complaint c = Complaint.register(ComplaintChannel.EMAIL, "POL-1", "overcharged", null, now);
        c.acknowledge(now, Duration.ofDays(2));
        return c;
    }

    @Test
    void acknowledge_loads_mutates_persists_and_audits() {
        UUID id = UUID.randomUUID();
        Complaint registered = Complaint.register(ComplaintChannel.EMAIL, "POL-1", "overcharged", null, now);
        when(complaints.findById(id)).thenReturn(Optional.of(registered));

        lifecycle.acknowledge(id, "agent.kanmegnea");

        assertThat(registered.status()).isEqualTo(ComplaintStatus.ACKNOWLEDGED);
        assertThat(registered.acknowledgementDueAt()).isEqualTo(now.plus(Duration.ofDays(2)));
        verify(complaints).save(registered);
        verify(audit).record(eq("complaint"), eq(id), eq("COMPLAINT_ACKNOWLEDGED"), eq("agent.kanmegnea"),
                any());
    }

    @Test
    void assign_records_the_handler_and_audits() {
        UUID id = UUID.randomUUID();
        when(complaints.findById(id)).thenReturn(Optional.of(acknowledged()));

        lifecycle.assign(id, "adjuster.sophie", "agent.kanmegnea");

        verify(complaints).save(any(Complaint.class));
        verify(audit).record(eq("complaint"), eq(id), eq("COMPLAINT_ASSIGNED"), eq("agent.kanmegnea"), any());
    }

    @Test
    void an_unknown_id_is_not_found_and_writes_nothing() {
        UUID id = UUID.randomUUID();
        when(complaints.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lifecycle.acknowledge(id, "agent"))
                .isInstanceOf(ComplaintNotFoundException.class);
        verify(complaints, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void an_illegal_transition_propagates_and_writes_nothing() {
        UUID id = UUID.randomUUID();
        // a freshly REGISTERED complaint cannot be resolved
        Complaint registered = Complaint.register(ComplaintChannel.EMAIL, "POL-1", "x", null, now);
        when(complaints.findById(id)).thenReturn(Optional.of(registered));

        assertThatThrownBy(() -> lifecycle.resolve(id, "note", "agent"))
                .isInstanceOf(RuntimeException.class); // IllegalComplaintTransitionException from the aggregate
        verify(complaints, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }
}
