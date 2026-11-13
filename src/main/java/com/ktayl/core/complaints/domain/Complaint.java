package com.ktayl.core.complaints.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The Complaint aggregate — PURE business logic (hexagonal, ADR-013): zero framework / persistence / web
 * imports, so every rule is testable with no Spring context and no database, and the tech underneath
 * (JPA, Postgres, REST) can change without touching this class.
 *
 * <p>CMPL-011 covers {@link #register}. CMPL-012 adds the forward-only lifecycle state machine
 * {@code REGISTERED → ACKNOWLEDGED → ASSIGNED → RESOLVED → CLOSED} ({@link #acknowledge}/{@link #assign}/
 * {@link #resolve}/{@link #close}) — each guarded so it runs only from its legal predecessor (else
 * {@link IllegalComplaintTransitionException}), plus the acknowledgement-due <em>placeholder</em> clock.
 *
 * <p>Reconstitution ({@link #rehydrate}) exists only for the persistence adapter to rebuild an aggregate
 * from stored state — the domain never knows it is being persisted.
 */
public final class Complaint {

    private final UUID id;
    private final ComplaintChannel channel;
    private final String subjectRef;   // party/policy identifier (nullable) — reference by id, never a cross-schema join
    private final String summary;
    private final Instant registeredAt;
    private final String idempotencyKey; // nullable — safe-retry key (POST-retry safety, NOT content dedup)
    private ComplaintStatus status;
    // mutable lifecycle state (set as the complaint advances through the state machine):
    private Instant acknowledgementDueAt; // placeholder next-action clock — NOT the statutory deadline
    private String assignedTo;            // set on assign
    private String resolutionNote;        // set on resolve

    private Complaint(UUID id, ComplaintChannel channel, String subjectRef, String summary,
            Instant registeredAt, String idempotencyKey, ComplaintStatus status,
            Instant acknowledgementDueAt, String assignedTo, String resolutionNote) {
        this.id = id;
        this.channel = channel;
        this.subjectRef = subjectRef;
        this.summary = summary;
        this.registeredAt = registeredAt;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.acknowledgementDueAt = acknowledgementDueAt;
        this.assignedTo = assignedTo;
        this.resolutionNote = resolutionNote;
    }

    /**
     * Register a new complaint. Enforces the registration invariants (a complaint must have a channel and a
     * non-blank summary) and returns a fresh aggregate in {@link ComplaintStatus#REGISTERED}.
     *
     * @throws InvalidComplaintException if the channel is null or the summary is blank
     */
    public static Complaint register(ComplaintChannel channel, String subjectRef, String summary,
            String idempotencyKey, Instant now) {
        if (channel == null) {
            throw new InvalidComplaintException("channel is required");
        }
        if (summary == null || summary.isBlank()) {
            throw new InvalidComplaintException("summary is required");
        }
        return new Complaint(UUID.randomUUID(), channel, subjectRef, summary.strip(), now, idempotencyKey,
                ComplaintStatus.REGISTERED, null, null, null);
    }

    /** Rebuild an aggregate from persisted state (used only by the persistence adapter). */
    public static Complaint rehydrate(UUID id, ComplaintChannel channel, String subjectRef, String summary,
            Instant registeredAt, String idempotencyKey, ComplaintStatus status, Instant acknowledgementDueAt,
            String assignedTo, String resolutionNote) {
        return new Complaint(id, channel, subjectRef, summary, registeredAt, idempotencyKey, status,
                acknowledgementDueAt, assignedTo, resolutionNote);
    }

    // ── Lifecycle (CMPL-012) — forward-only; each guarded by its legal predecessor ─────────────────────

    /**
     * Acknowledge receipt (REGISTERED → ACKNOWLEDGED) and set the acknowledgement-due clock to
     * {@code now + window}. <strong>Placeholder</strong>: this is a next-action SLA marker, deliberately NOT
     * claimed to be the ACPR/EIOPA/IDD statutory deadline (that computation is out of scope here).
     *
     * @throws IllegalComplaintTransitionException if not currently REGISTERED
     */
    public void acknowledge(Instant now, Duration acknowledgementWindow) {
        requireStatus(ComplaintStatus.REGISTERED);
        this.status = ComplaintStatus.ACKNOWLEDGED;
        this.acknowledgementDueAt = now.plus(acknowledgementWindow);
    }

    /**
     * Assign to a handler (ACKNOWLEDGED → ASSIGNED).
     *
     * @throws IllegalComplaintTransitionException if not currently ACKNOWLEDGED
     * @throws InvalidComplaintException           if {@code assignedTo} is blank
     */
    public void assign(String assignedTo) {
        requireStatus(ComplaintStatus.ACKNOWLEDGED);
        if (assignedTo == null || assignedTo.isBlank()) {
            throw new InvalidComplaintException("assignedTo is required");
        }
        this.status = ComplaintStatus.ASSIGNED;
        this.assignedTo = assignedTo.strip();
    }

    /**
     * Record the resolution (ASSIGNED → RESOLVED).
     *
     * @throws IllegalComplaintTransitionException if not currently ASSIGNED
     * @throws InvalidComplaintException           if {@code resolutionNote} is blank
     */
    public void resolve(String resolutionNote) {
        requireStatus(ComplaintStatus.ASSIGNED);
        if (resolutionNote == null || resolutionNote.isBlank()) {
            throw new InvalidComplaintException("resolution note is required");
        }
        this.status = ComplaintStatus.RESOLVED;
        this.resolutionNote = resolutionNote.strip();
    }

    /**
     * Close the complaint (RESOLVED → CLOSED). A second close (or close from any non-RESOLVED state) fails.
     *
     * @throws IllegalComplaintTransitionException if not currently RESOLVED
     */
    public void close() {
        requireStatus(ComplaintStatus.RESOLVED);
        this.status = ComplaintStatus.CLOSED;
    }

    /**
     * Whether the acknowledgement-due clock has elapsed at {@code now}. Strictly-after semantics: at exactly
     * {@code acknowledgementDueAt} it is NOT yet overdue (the {@code <} vs {@code <=} boundary). False when
     * the clock was never set (not yet acknowledged).
     */
    public boolean acknowledgementOverdueAt(Instant now) {
        return acknowledgementDueAt != null && now.isAfter(acknowledgementDueAt);
    }

    private void requireStatus(ComplaintStatus required) {
        if (this.status != required) {
            throw new IllegalComplaintTransitionException(this.status, required);
        }
    }

    /** The domain event raised when a complaint is registered (published in-process — ADR-007). */
    public ComplaintRegistered registeredEvent() {
        return new ComplaintRegistered(id, channel, registeredAt);
    }

    public UUID id() { return id; }
    public ComplaintChannel channel() { return channel; }
    public String subjectRef() { return subjectRef; }
    public String summary() { return summary; }
    public Instant registeredAt() { return registeredAt; }
    public String idempotencyKey() { return idempotencyKey; }
    public ComplaintStatus status() { return status; }
    public Instant acknowledgementDueAt() { return acknowledgementDueAt; }
    public String assignedTo() { return assignedTo; }
    public String resolutionNote() { return resolutionNote; }
}
