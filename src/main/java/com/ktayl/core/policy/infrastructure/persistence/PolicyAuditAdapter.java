package com.ktayl.core.policy.infrastructure.persistence;

import com.ktayl.core.policy.application.PolicyAuditLog;
import com.ktayl.core.policy.domain.PolicyStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Writes the DORA-Art.9 policy audit trail to {@code policy.policy_audit_log} (BF-POL-03). */
@Component
class PolicyAuditAdapter implements PolicyAuditLog {

    private final PolicyAuditJpaRepository jpa;

    PolicyAuditAdapter(PolicyAuditJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void record(UUID policyId, String action, PolicyStatus from, PolicyStatus to, String actor, String reason) {
        jpa.save(new PolicyAuditEntity(policyId, action, from.name(), to.name(), actor, reason, Instant.now()));
    }
}
