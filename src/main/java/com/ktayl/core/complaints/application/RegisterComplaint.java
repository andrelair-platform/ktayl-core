package com.ktayl.core.complaints.application;

import com.ktayl.core.complaints.domain.Complaint;
import com.ktayl.core.complaints.domain.ComplaintRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case: register a complaint (CMPL-011). Orchestrates the pure {@link Complaint} domain and the outbound
 * ports ({@link ComplaintRepository}, {@link AuditTrail}) — it holds NO business rules (those live on the
 * aggregate) and NO persistence detail (that lives in the adapter). The only framework here is the Spring
 * wiring (@Service/@Transactional) and the in-process event bus (ADR-007), which is the allowed application
 * layer per ADR-013 — so the domain stays tech-independent.
 */
@Service
public class RegisterComplaint {

    private static final Logger log = LoggerFactory.getLogger(RegisterComplaint.class);

    private final ComplaintRepository complaints;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RegisterComplaint(ComplaintRepository complaints, AuditTrail audit,
            ApplicationEventPublisher events, Clock clock) {
        this.complaints = complaints;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ComplaintRegistration handle(RegisterComplaintCommand command, String actor) {
        // Idempotent retry: same key → return the already-registered complaint, no second event/audit.
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<Complaint> existing = complaints.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                log.info("complaint register idempotent-hit key={} id={}", command.idempotencyKey(),
                        existing.get().id());
                return new ComplaintRegistration(existing.get().id(), false);
            }
        }

        Complaint complaint = Complaint.register(command.channel(), command.subjectRef(), command.summary(),
                command.idempotencyKey(), Instant.now(clock));
        Complaint saved = complaints.save(complaint);

        audit.record("complaint", saved.id(), "COMPLAINT_REGISTERED", actor,
                "channel=" + saved.channel() + " subjectRef=" + saved.subjectRef());
        events.publishEvent(saved.registeredEvent());
        log.info("complaint registered id={} channel={}", saved.id(), saved.channel());

        return new ComplaintRegistration(saved.id(), true);
    }
}
