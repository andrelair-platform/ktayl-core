package com.ktayl.core.complaints.api;

import java.util.UUID;

/**
 * The complaints module's published inbound port (ADR-005/013) — the single entry point for registering and
 * advancing a complaint, driven by the REST adapter and callable in-process by other modules. Expressed in
 * java/primitive types only (no internal domain types leak across the boundary); {@code channel} is a String
 * parsed by the implementation (an unknown value is rejected as invalid input → 4xx). {@code actor} is the
 * authenticated principal, threaded through for audit attribution (never a placeholder).
 *
 * <p>Failure contract (mapped to HTTP by the web adapter): unknown id → {@code ComplaintNotFoundException}
 * (404), illegal transition → {@code IllegalComplaintTransitionException} (409), bad input →
 * {@code InvalidComplaintException} (4xx).
 */
public interface ComplaintOperations {

    /** Register a complaint; returns its id. Safe to retry with the same {@code idempotencyKey}. */
    UUID register(String channel, String subjectRef, String summary, String idempotencyKey, String actor);

    void acknowledge(UUID id, String actor);

    void assign(UUID id, String assignedTo, String actor);

    void resolve(UUID id, String resolutionNote, String actor);

    void close(UUID id, String actor);

    /** @throws com.ktayl.core.complaints.domain.ComplaintNotFoundException if no complaint has that id */
    ComplaintSummary get(UUID id);
}
