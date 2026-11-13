package com.ktayl.core.complaints.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model for a complaint — an infrastructure adapter type, NOT the domain aggregate (ADR-013).
 * Deliberately dumb: plain columns (channel/status as Strings, mapped to the domain enums by
 * {@link ComplaintPersistenceAdapter}), no behaviour. Explicit {@code schema = "complaints"} because the
 * global {@code default_schema} is billing (schema-per-module, ADR-006).
 */
@Entity
@Table(name = "complaint", schema = "complaints")
public class ComplaintJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String channel;

    @Column(name = "subject_ref")
    private String subjectRef;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private String status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    // lifecycle state (CMPL-012) — null until the relevant transition occurs
    @Column(name = "acknowledgement_due_at")
    private Instant acknowledgementDueAt;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "resolution_note")
    private String resolutionNote;

    protected ComplaintJpaEntity() {} // JPA

    public ComplaintJpaEntity(UUID id, String channel, String subjectRef, String summary, String status,
            Instant registeredAt, String idempotencyKey, Instant acknowledgementDueAt, String assignedTo,
            String resolutionNote) {
        this.id = id;
        this.channel = channel;
        this.subjectRef = subjectRef;
        this.summary = summary;
        this.status = status;
        this.registeredAt = registeredAt;
        this.idempotencyKey = idempotencyKey;
        this.acknowledgementDueAt = acknowledgementDueAt;
        this.assignedTo = assignedTo;
        this.resolutionNote = resolutionNote;
    }

    public UUID getId() { return id; }
    public String getChannel() { return channel; }
    public String getSubjectRef() { return subjectRef; }
    public String getSummary() { return summary; }
    public String getStatus() { return status; }
    public Instant getRegisteredAt() { return registeredAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getAcknowledgementDueAt() { return acknowledgementDueAt; }
    public String getAssignedTo() { return assignedTo; }
    public String getResolutionNote() { return resolutionNote; }
}
