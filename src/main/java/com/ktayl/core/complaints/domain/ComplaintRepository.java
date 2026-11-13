package com.ktayl.core.complaints.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * OUTBOUND PORT (hexagonal, ADR-013) — the domain's view of persistence. An interface, owned by the domain
 * and expressed in domain terms ({@link Complaint}); the JPA adapter in {@code infrastructure.persistence}
 * implements it. The application use case depends on THIS, never on Spring Data / JPA, so the storage tech
 * is swappable without touching the business logic.
 */
public interface ComplaintRepository {

    /** Persist (insert or update) an aggregate and return it. */
    Complaint save(Complaint complaint);

    Optional<Complaint> findById(UUID id);

    /**
     * Look up an existing complaint by its client-supplied idempotency key, for safe POST retries
     * (CMPL-011 AC). Returns empty when no key was supplied or none matches.
     */
    Optional<Complaint> findByIdempotencyKey(String idempotencyKey);
}
