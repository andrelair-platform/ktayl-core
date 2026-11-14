package com.ktayl.core.policy.domain;

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
}
