package com.ktayl.core.policy.application;

import com.ktayl.core.policy.domain.PolicyStatus;
import java.util.UUID;

/**
 * Outbound port for the DORA-Art.9 policy audit trail (BF-POL-03). One append per lifecycle transition.
 * The JPA adapter lives in {@code infrastructure.persistence}; the application depends only on this.
 */
public interface PolicyAuditLog {

    void record(UUID policyId, String action, PolicyStatus from, PolicyStatus to, String actor, String reason);
}
