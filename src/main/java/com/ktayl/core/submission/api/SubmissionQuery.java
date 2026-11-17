package com.ktayl.core.submission.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The submission module's published read port (ADR-005/013) — the in-process query contract other modules
 * (Distribution / underwriting) consume. Returns the published {@link SubmissionView}, never the internal
 * entity/aggregate.
 */
public interface SubmissionQuery {

    /** The submission for an id, if it exists. */
    Optional<SubmissionView> findById(UUID id);

    /**
     * A keyset page of submissions ordered by {@code received_at, id}, optionally filtered by {@code status}
     * (a String — an unknown value is rejected as {@code InvalidSubmissionException}). The cursor is the
     * decoded {@code (afterReceivedAt, afterId)} keyset (both null = first page); {@code limit} caps the page.
     * The web adapter owns the opaque cursor encode/decode (mirrors the policy / screening list pattern).
     */
    List<SubmissionView> list(String status, Instant afterReceivedAt, UUID afterId, int limit);
}
