package com.ktayl.core.complaints.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktayl.core.complaints.domain.Complaint;
import com.ktayl.core.complaints.domain.ComplaintChannel;
import com.ktayl.core.complaints.domain.ComplaintRegistered;
import com.ktayl.core.complaints.domain.ComplaintRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * L1 — the RegisterComplaint use case against mocked ports (no Spring, no DB). Proves orchestration:
 * persist + audit + publish on a new complaint, and the idempotent short-circuit on a repeated key.
 */
class RegisterComplaintTest {

    private final Instant now = Instant.parse("2026-11-13T10:15:30Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private final AuditTrail audit = mock(AuditTrail.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RegisterComplaint useCase = new RegisterComplaint(complaints, audit, events, clock);

    @Test
    void registers_persists_audits_and_publishes_the_event() {
        when(complaints.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(complaints.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0)); // echo the saved aggregate

        var cmd = new RegisterComplaintCommand(ComplaintChannel.EMAIL, "POL-42", "premium overcharged", "idem-1");
        ComplaintRegistration result = useCase.handle(cmd);

        assertThat(result.created()).isTrue();
        assertThat(result.complaintId()).isNotNull();
        verify(complaints).save(any(Complaint.class));
        verify(audit).record(eq("complaint"), eq(result.complaintId()), eq("COMPLAINT_REGISTERED"), any(), any());
        verify(events).publishEvent(any(ComplaintRegistered.class));
    }

    @Test
    void is_idempotent_on_a_repeated_key_no_second_write_or_event() {
        Complaint existing = Complaint.register(ComplaintChannel.EMAIL, "POL-42", "premium overcharged", "idem-1", now);
        when(complaints.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        var cmd = new RegisterComplaintCommand(ComplaintChannel.EMAIL, "POL-42", "premium overcharged", "idem-1");
        ComplaintRegistration result = useCase.handle(cmd);

        assertThat(result.created()).isFalse();
        assertThat(result.complaintId()).isEqualTo(existing.id());
        verify(complaints, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void a_blank_key_is_treated_as_no_key_and_always_registers() {
        when(complaints.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new RegisterComplaintCommand(ComplaintChannel.PHONE, null, "call dropped", "   ");
        ComplaintRegistration result = useCase.handle(cmd);

        assertThat(result.created()).isTrue();
        verify(complaints, never()).findByIdempotencyKey(any()); // blank key → no lookup
        verify(complaints).save(any(Complaint.class));
    }
}
