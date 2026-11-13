package com.ktayl.core.complaints.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * The Complaint aggregate — PURE business logic (hexagonal, ADR-013): zero framework / persistence / web
 * imports, so every rule is testable with no Spring context and no database, and the tech underneath
 * (JPA, Postgres, REST) can change without touching this class.
 *
 * <p>CMPL-011 covers {@link #register}: a newly registered complaint starts in {@link ComplaintStatus#REGISTERED}.
 * The forward-only state machine ({@code acknowledge → assign → resolve → close}) and the statutory-ack
 * clock land in CMPL-012.
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

    private Complaint(UUID id, ComplaintChannel channel, String subjectRef, String summary,
            Instant registeredAt, String idempotencyKey, ComplaintStatus status) {
        this.id = id;
        this.channel = channel;
        this.subjectRef = subjectRef;
        this.summary = summary;
        this.registeredAt = registeredAt;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
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
                ComplaintStatus.REGISTERED);
    }

    /** Rebuild an aggregate from persisted state (used only by the persistence adapter). */
    public static Complaint rehydrate(UUID id, ComplaintChannel channel, String subjectRef, String summary,
            Instant registeredAt, String idempotencyKey, ComplaintStatus status) {
        return new Complaint(id, channel, subjectRef, summary, registeredAt, idempotencyKey, status);
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
}
