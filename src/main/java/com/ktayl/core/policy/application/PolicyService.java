package com.ktayl.core.policy.application;

import com.ktayl.core.policy.domain.Policy;
import com.ktayl.core.policy.domain.PolicyAlreadyExistsException;
import com.ktayl.core.policy.domain.PolicyNotFoundException;
import com.ktayl.core.policy.domain.PolicyRepository;
import com.ktayl.core.policy.domain.PolicyStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Policy lifecycle use cases (ADR-014 Phase 1 — the bind path folded in from ktayl-policy-service). Spring
 * wiring only; the rules live in the {@link Policy} aggregate. Every transition writes a DORA-Art.9 audit
 * row (BF-POL-03) with the acting principal, from→to and (for cancel) the reason. The create → submit →
 * activate sequence is exactly what underwriting's bind drives, so repointing it at ktayl-core (Phase 3)
 * needs no UW code change.
 */
@Service
public class PolicyService {

    private final PolicyRepository policies;
    private final PolicyAuditLog audit;

    public PolicyService(PolicyRepository policies, PolicyAuditLog audit) {
        this.policies = policies;
        this.audit = audit;
    }

    /**
     * Create a DRAFT policy. Throws {@link PolicyAlreadyExistsException} (→409) if the policy_number exists
     * — mirrors the PAS; the caller resolves the existing one via {@link #findByPolicyNumber}.
     */
    @Transactional
    public Policy create(CreatePolicyCommand cmd, String actor) {
        if (cmd != null && cmd.policyNumber() != null && policies.findByPolicyNumber(cmd.policyNumber()).isPresent()) {
            throw new PolicyAlreadyExistsException("policy " + cmd.policyNumber() + " already exists");
        }
        Policy policy = Policy.create(cmd.policyNumber(), cmd.holderName(), cmd.productCode(),
                cmd.effectiveDate(), cmd.expiryDate(), Instant.now());
        policies.save(policy);
        audit.record(policy.id(), "CREATE", PolicyStatus.DRAFT, policy.status(), actor, null);
        return policy;
    }

    @Transactional
    public Policy submit(UUID id, String actor) {
        return transition(id, actor, "SUBMIT", null, Policy::submit);
    }

    @Transactional
    public Policy activate(UUID id, String actor) {
        return transition(id, actor, "ACTIVATE", null, Policy::activate);
    }

    @Transactional
    public Policy cancel(UUID id, String reason, String actor) {
        return transition(id, actor, "CANCEL", reason, (p, now) -> p.cancel(reason, now));
    }

    @Transactional(readOnly = true)
    public Policy get(UUID id) {
        return require(id);
    }

    @Transactional(readOnly = true)
    public Optional<Policy> findByPolicyNumber(String policyNumber) {
        return policies.findByPolicyNumber(policyNumber);
    }

    private Policy transition(UUID id, String actor, String action, String reason, Transition fn) {
        Policy policy = require(id);
        PolicyStatus from = policy.status();
        fn.apply(policy, Instant.now());
        policies.save(policy);
        audit.record(policy.id(), action, from, policy.status(), actor, reason);
        return policy;
    }

    private Policy require(UUID id) {
        return policies.findById(id).orElseThrow(() -> new PolicyNotFoundException("no policy " + id));
    }

    @FunctionalInterface
    private interface Transition {
        void apply(Policy policy, Instant now);
    }
}
