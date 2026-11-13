package com.ktayl.core.complaints.application;

import com.ktayl.core.complaints.domain.Complaint;
import com.ktayl.core.complaints.domain.ComplaintNotFoundException;
import com.ktayl.core.complaints.domain.ComplaintRepository;
import com.ktayl.core.complaints.domain.ComplaintStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for the complaint lifecycle (CMPL-012): {@code acknowledge → assign → resolve → close}. Each
 * loads the aggregate (→ {@link ComplaintNotFoundException} if absent), applies the <em>guarded</em> domain
 * operation (the {@link Complaint} state machine throws on an illegal transition / bad input), persists it,
 * and writes one append-only audit row recording the {@code from → to} transition. Holds NO business rules
 * (those live on the aggregate) and NO persistence detail (that lives in the adapter) — ADR-013.
 */
@Service
public class ComplaintLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ComplaintLifecycle.class);

    private final ComplaintRepository complaints;
    private final AuditTrail audit;
    private final Clock clock;
    private final Duration acknowledgementWindow;

    public ComplaintLifecycle(ComplaintRepository complaints, AuditTrail audit, Clock clock,
            @Value("${complaints.acknowledgement-window:P2D}") Duration acknowledgementWindow) {
        this.complaints = complaints;
        this.audit = audit;
        this.clock = clock;
        this.acknowledgementWindow = acknowledgementWindow;
    }

    @Transactional
    public void acknowledge(UUID id, String actor) {
        transition(id, actor, "COMPLAINT_ACKNOWLEDGED",
                c -> c.acknowledge(Instant.now(clock), acknowledgementWindow));
    }

    @Transactional
    public void assign(UUID id, String assignedTo, String actor) {
        transition(id, actor, "COMPLAINT_ASSIGNED", c -> c.assign(assignedTo));
    }

    @Transactional
    public void resolve(UUID id, String resolutionNote, String actor) {
        transition(id, actor, "COMPLAINT_RESOLVED", c -> c.resolve(resolutionNote));
    }

    @Transactional
    public void close(UUID id, String actor) {
        transition(id, actor, "COMPLAINT_CLOSED", Complaint::close);
    }

    private void transition(UUID id, String actor, String action, Consumer<Complaint> operation) {
        Complaint complaint = complaints.findById(id).orElseThrow(() -> new ComplaintNotFoundException(id));
        ComplaintStatus from = complaint.status();
        operation.accept(complaint);            // guarded by the aggregate — throws before any write on a bad move
        complaints.save(complaint);
        audit.record("complaint", id, action, actor, "from=" + from + " to=" + complaint.status());
        log.info("complaint {} {} by {} ({} -> {})", id, action, actor, from, complaint.status());
    }
}
