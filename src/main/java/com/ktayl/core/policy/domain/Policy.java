package com.ktayl.core.policy.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Policy aggregate — PURE business logic (hexagonal, ADR-013): zero framework / persistence / web
 * imports, so every rule is testable without Spring or a database. This is the ktayl-core home for the PAS
 * policy registry being folded in from ktayl-policy-service (ADR-014, Phase 1 — the bind path).
 *
 * <p>Lifecycle (mirrors the PAS): {@code DRAFT → SUBMITTED → ACTIVE → AMENDED}, with {@code SUBMITTED →
 * REJECTED} and {@code ACTIVE → CANCELLED / EXPIRED}. Underwriting's bind drives create → submit → activate.
 * Each transition runs only from its legal predecessor, else {@link IllegalPolicyTransitionException}.
 *
 * <p>{@link #rehydrate} exists only for the persistence adapter to rebuild an aggregate from stored state.
 */
public final class Policy {

    private final UUID id;
    private final String policyNumber;   // the shared key, deterministic from the quote (idempotency)
    private String holderName;           // mutable only via update(), and only while DRAFT
    private String productCode;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private final Instant createdAt;
    private PolicyStatus status;
    private Instant updatedAt;

    private Policy(UUID id, String policyNumber, String holderName, String productCode,
            LocalDate effectiveDate, LocalDate expiryDate, PolicyStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.policyNumber = policyNumber;
        this.holderName = holderName;
        this.productCode = productCode;
        this.effectiveDate = effectiveDate;
        this.expiryDate = expiryDate;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Create a new policy in {@link PolicyStatus#DRAFT}. Enforces the required fields + date ordering. */
    public static Policy create(String policyNumber, String holderName, String productCode,
            LocalDate effectiveDate, LocalDate expiryDate, Instant now) {
        if (isBlank(policyNumber)) throw new InvalidPolicyException("policy_number is required");
        if (isBlank(holderName)) throw new InvalidPolicyException("holder_name is required");
        if (isBlank(productCode)) throw new InvalidPolicyException("product_code is required");
        if (effectiveDate == null || expiryDate == null) throw new InvalidPolicyException("effective/expiry dates are required");
        if (!effectiveDate.isBefore(expiryDate)) throw new InvalidPolicyException("effective_date must be before expiry_date");
        return new Policy(UUID.randomUUID(), policyNumber, holderName, productCode, effectiveDate, expiryDate,
                PolicyStatus.DRAFT, now, now);
    }

    /**
     * Update the mutable details (holder, product, dates) — allowed ONLY while DRAFT (BF-POL-04). Once a
     * policy is submitted/active, a change is an <em>amendment</em> (its own lifecycle), not an edit; this
     * guards against silently rewriting a live contract. Re-validates the required fields + date ordering.
     */
    public void update(String holderName, String productCode, LocalDate effectiveDate, LocalDate expiryDate, Instant now) {
        requireStatus(PolicyStatus.DRAFT, "update");
        if (isBlank(holderName)) throw new InvalidPolicyException("holder_name is required");
        if (isBlank(productCode)) throw new InvalidPolicyException("product_code is required");
        if (effectiveDate == null || expiryDate == null) throw new InvalidPolicyException("effective/expiry dates are required");
        if (!effectiveDate.isBefore(expiryDate)) throw new InvalidPolicyException("effective_date must be before expiry_date");
        this.holderName = holderName;
        this.productCode = productCode;
        this.effectiveDate = effectiveDate;
        this.expiryDate = expiryDate;
        this.updatedAt = now;
    }

    /** DRAFT → SUBMITTED. */
    public void submit(Instant now) {
        requireStatus(PolicyStatus.DRAFT, "submit");
        transitionTo(PolicyStatus.SUBMITTED, now);
    }

    /** SUBMITTED → ACTIVE (this is "bind"). */
    public void activate(Instant now) {
        requireStatus(PolicyStatus.SUBMITTED, "activate");
        transitionTo(PolicyStatus.ACTIVE, now);
    }

    /** SUBMITTED → REJECTED (underwriter rejects). */
    public void reject(Instant now) {
        requireStatus(PolicyStatus.SUBMITTED, "reject");
        transitionTo(PolicyStatus.REJECTED, now);
    }

    /** ACTIVE → AMENDED (a mid-term change is in flight). */
    public void amend(Instant now) {
        requireStatus(PolicyStatus.ACTIVE, "amend");
        transitionTo(PolicyStatus.AMENDED, now);
    }

    /** AMENDED → ACTIVE (the amendment is confirmed). */
    public void confirmAmendment(Instant now) {
        requireStatus(PolicyStatus.AMENDED, "confirm amendment");
        transitionTo(PolicyStatus.ACTIVE, now);
    }

    /** ACTIVE → CANCELLED (soft-cancel; a reason is mandatory — BF-POL-08). */
    public void cancel(String reason, Instant now) {
        requireStatus(PolicyStatus.ACTIVE, "cancel");
        if (reason == null || reason.isBlank()) {
            throw new InvalidPolicyException("a cancellation reason is required");
        }
        transitionTo(PolicyStatus.CANCELLED, now);
    }

    /** ACTIVE → EXPIRED (automatic at expiry_date — the batch job, deferred post-Phase-1). */
    public void expire(Instant now) {
        requireStatus(PolicyStatus.ACTIVE, "expire");
        transitionTo(PolicyStatus.EXPIRED, now);
    }

    private void transitionTo(PolicyStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
    }

    private void requireStatus(PolicyStatus expected, String action) {
        if (this.status != expected) {
            throw new IllegalPolicyTransitionException(
                    "cannot " + action + " a policy in state " + this.status + " (requires " + expected + ")");
        }
    }

    /** Rebuild from stored state (persistence only — the domain never knows it is persisted). */
    public static Policy rehydrate(UUID id, String policyNumber, String holderName, String productCode,
            LocalDate effectiveDate, LocalDate expiryDate, PolicyStatus status, Instant createdAt, Instant updatedAt) {
        return new Policy(id, policyNumber, holderName, productCode, effectiveDate, expiryDate, status, createdAt, updatedAt);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    public UUID id() { return id; }
    public String policyNumber() { return policyNumber; }
    public String holderName() { return holderName; }
    public String productCode() { return productCode; }
    public LocalDate effectiveDate() { return effectiveDate; }
    public LocalDate expiryDate() { return expiryDate; }
    public PolicyStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
