package com.ktayl.core.policy.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for policy persistence (hexagonal, ADR-013). The domain depends on this interface; the JPA
 * adapter lives in {@code infrastructure.persistence}. No framework types leak through it.
 */
public interface PolicyRepository {

    Policy save(Policy policy);

    Optional<Policy> findById(UUID id);

    Optional<Policy> findByPolicyNumber(String policyNumber);

    /**
     * Keyset page (BF-POL-04), ordered by {@code (createdAt, id)} ascending. {@code status} filters when
     * non-null; {@code afterCreatedAt}/{@code afterId} are the decoded cursor (null = first page). Returns at
     * most {@code limit} rows — the caller emits a next-cursor when a full page comes back.
     */
    List<Policy> list(PolicyStatus status, Instant afterCreatedAt, UUID afterId, int limit);
}
