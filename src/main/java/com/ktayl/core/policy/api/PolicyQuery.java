package com.ktayl.core.policy.api;

import java.util.Optional;
import java.util.UUID;

/**
 * The policy module's PUBLISHED read interface (ADR-005 module ownership). Other ktayl-core modules read
 * policy data through THIS — an in-process call — instead of the internal tables or a cross-service HTTP
 * hop. This is the shared-kernel value of folding the PAS in (ADR-014): billing, claims, etc. get policy
 * data without the network. Returns the {@link PolicyView} read model, never the domain aggregate.
 */
public interface PolicyQuery {

    Optional<PolicyView> findByPolicyNumber(String policyNumber);

    Optional<PolicyView> findById(UUID id);
}
