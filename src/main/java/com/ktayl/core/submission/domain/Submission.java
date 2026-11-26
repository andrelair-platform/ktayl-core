package com.ktayl.core.submission.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * The Submission aggregate — PURE business logic (hexagonal, ADR-013): zero framework / persistence / web
 * imports, so every rule is testable with no Spring context and no database, and the tech underneath (JPA,
 * Postgres, REST) can change without touching this class.
 *
 * <p>SUB-01 covers {@link #receive} + the forward-only lifecycle state machine
 * {@code RECEIVED → TRIAGED → ROUTED} (or {@code → DECLINED}): {@link #triage}, {@link #route},
 * {@link #decline} are each guarded so they run only from a legal predecessor (else
 * {@link IllegalSubmissionTransitionException}). A sanctions {@link #flagScreeningHold(UUID)} at intake sets
 * a hold that {@link #route} refuses ({@link SubmissionScreeningHoldException}) until {@link #clearScreeningHold}
 * — a held submission is captured + triageable but never routed to underwriting while held (HITL).
 *
 * <p>Reconstitution ({@link #rehydrate}) exists only for the persistence adapter to rebuild an aggregate
 * from stored state — the domain never knows it is being persisted.
 */
public final class Submission {

    private final UUID id;
    private final String brokerRef;
    private final String insuredName;
    private final String productLine;
    private final Instant receivedAt;
    private SubmissionStatus status;
    private boolean screeningHold;
    private UUID screeningAlertId;   // the screening alert opened at intake (null unless held)
    private String routedTarget;     // set on route
    private String uwSubmissionRef;  // the underwriting submission id from the handoff (null until handed off)
    private Instant updatedAt;

    private Submission(UUID id, String brokerRef, String insuredName, String productLine, Instant receivedAt,
            SubmissionStatus status, boolean screeningHold, UUID screeningAlertId, String routedTarget,
            String uwSubmissionRef, Instant updatedAt) {
        this.id = id;
        this.brokerRef = brokerRef;
        this.insuredName = insuredName;
        this.productLine = productLine;
        this.receivedAt = receivedAt;
        this.status = status;
        this.screeningHold = screeningHold;
        this.screeningAlertId = screeningAlertId;
        this.routedTarget = routedTarget;
        this.uwSubmissionRef = uwSubmissionRef;
        this.updatedAt = updatedAt;
    }

    /**
     * Receive a new broker submission. Enforces the intake invariants (a broker ref, an insured name, a
     * product line) and returns a fresh aggregate in {@link SubmissionStatus#RECEIVED}, not held.
     *
     * @throws InvalidSubmissionException if the broker ref, insured name or product line is blank
     */
    public static Submission receive(String brokerRef, String insuredName, String productLine, Instant now) {
        if (brokerRef == null || brokerRef.isBlank()) {
            throw new InvalidSubmissionException("broker ref is required");
        }
        if (insuredName == null || insuredName.isBlank()) {
            throw new InvalidSubmissionException("insured name is required");
        }
        if (productLine == null || productLine.isBlank()) {
            throw new InvalidSubmissionException("product line is required");
        }
        return new Submission(UUID.randomUUID(), brokerRef.strip(), insuredName.strip(), productLine.strip(),
                now, SubmissionStatus.RECEIVED, false, null, null, null, now);
    }

    /** Rebuild an aggregate from persisted state (used only by the persistence adapter). */
    @SuppressWarnings("checkstyle:ParameterNumber") // data-carrier: the args ARE the persisted fields
    public static Submission rehydrate(UUID id, String brokerRef, String insuredName, String productLine,
            Instant receivedAt, SubmissionStatus status, boolean screeningHold, UUID screeningAlertId,
            String routedTarget, String uwSubmissionRef, Instant updatedAt) {
        return new Submission(id, brokerRef, insuredName, productLine, receivedAt, status, screeningHold,
                screeningAlertId, routedTarget, uwSubmissionRef, updatedAt);
    }

    // ── Intake screening (RC-02) ────────────────────────────────────────────────────────────────────────

    /**
     * Flag a sanctions/AML HOLD raised at intake, recording the screening alert. The submission is still
     * captured (not rejected) but cannot be {@link #route routed} until {@link #clearScreeningHold cleared}
     * by a human (HITL). Idempotent re-flags keep the first alert reference.
     */
    public void flagScreeningHold(UUID alertId) {
        this.screeningHold = true;
        if (this.screeningAlertId == null) {
            this.screeningAlertId = alertId;
        }
    }

    /** Clear the sanctions hold (the compliance HITL outcome) so the submission can be routed. */
    public void clearScreeningHold(Instant now) {
        this.screeningHold = false;
        this.updatedAt = now;
    }

    // ── Lifecycle (SUB-01) — forward-only; each guarded by its legal predecessor ─────────────────────────

    /**
     * Triage the submission. A {@link TriageDecision#ROUTE} moves {@code RECEIVED → TRIAGED} (making it
     * routable); a {@link TriageDecision#DECLINE} moves {@code RECEIVED → DECLINED}; a
     * {@link TriageDecision#RETURN} records a request for more information without changing the status. Any
     * triage must start from {@code RECEIVED}.
     *
     * @throws IllegalSubmissionTransitionException if not currently RECEIVED
     * @throws InvalidSubmissionException           if the decision is null
     */
    public void triage(TriageDecision decision, Instant now) {
        if (decision == null) {
            throw new InvalidSubmissionException("triage decision is required");
        }
        requireStatus(SubmissionStatus.RECEIVED);
        switch (decision) {
            case ROUTE -> this.status = SubmissionStatus.TRIAGED;
            case DECLINE -> this.status = SubmissionStatus.DECLINED;
            case RETURN -> { /* a return-to-broker records intent; status stays RECEIVED */ }
        }
        this.updatedAt = now;
    }

    /**
     * Route a triaged submission to a target (e.g. underwriting): {@code TRIAGED → ROUTED}. Refuses a
     * submission under a sanctions hold.
     *
     * @throws SubmissionScreeningHoldException     if the submission is under a screening hold (clear it first)
     * @throws IllegalSubmissionTransitionException if not currently TRIAGED
     * @throws InvalidSubmissionException           if {@code target} is blank
     */
    public void route(String target, Instant now) {
        if (this.screeningHold) {
            throw new SubmissionScreeningHoldException(this.id, this.screeningAlertId);
        }
        requireStatus(SubmissionStatus.TRIAGED);
        if (target == null || target.isBlank()) {
            throw new InvalidSubmissionException("routing target is required");
        }
        this.status = SubmissionStatus.ROUTED;
        this.routedTarget = target.strip();
        this.updatedAt = now;
    }

    /**
     * Record the underwriting submission id returned by a successful handoff (SUB-02). Only valid once the
     * submission is {@link SubmissionStatus#ROUTED} — the handoff happens in {@code route()} right after the
     * local ROUTED transition + save. Keeps the handoff result on the aggregate so the created UW submission
     * is traceable from the broker submission.
     *
     * @throws IllegalSubmissionTransitionException if not currently ROUTED
     * @throws InvalidSubmissionException           if {@code uwRef} is blank
     */
    public void recordUwHandoff(String uwRef) {
        requireStatus(SubmissionStatus.ROUTED);
        if (uwRef == null || uwRef.isBlank()) {
            throw new InvalidSubmissionException("underwriting submission ref is required");
        }
        this.uwSubmissionRef = uwRef.strip();
    }

    /**
     * Decline the submission directly (→ {@code DECLINED}) from {@code RECEIVED} or {@code TRIAGED} — the
     * explicit decline path, distinct from a triage DECLINE.
     *
     * @throws IllegalSubmissionTransitionException if already ROUTED or DECLINED
     * @throws InvalidSubmissionException           if {@code reason} is blank
     */
    public void decline(String reason, Instant now) {
        if (this.status != SubmissionStatus.RECEIVED && this.status != SubmissionStatus.TRIAGED) {
            throw new IllegalSubmissionTransitionException(this.status, SubmissionStatus.RECEIVED);
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidSubmissionException("decline reason is required");
        }
        this.status = SubmissionStatus.DECLINED;
        this.updatedAt = now;
    }

    private void requireStatus(SubmissionStatus required) {
        if (this.status != required) {
            throw new IllegalSubmissionTransitionException(this.status, required);
        }
    }

    public UUID id() { return id; }
    public String brokerRef() { return brokerRef; }
    public String insuredName() { return insuredName; }
    public String productLine() { return productLine; }
    public Instant receivedAt() { return receivedAt; }
    public SubmissionStatus status() { return status; }
    public boolean screeningHold() { return screeningHold; }
    public UUID screeningAlertId() { return screeningAlertId; }
    public String routedTarget() { return routedTarget; }
    public String uwSubmissionRef() { return uwSubmissionRef; }
    public Instant updatedAt() { return updatedAt; }
}
