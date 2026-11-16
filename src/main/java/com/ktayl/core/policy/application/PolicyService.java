package com.ktayl.core.policy.application;

import com.ktayl.core.policy.domain.Policy;
import com.ktayl.core.policy.domain.PolicyAlreadyExistsException;
import com.ktayl.core.policy.domain.PolicyNotFoundException;
import com.ktayl.core.policy.domain.PolicyRepository;
import com.ktayl.core.policy.domain.PolicyStatus;
import com.ktayl.core.screening.api.PartyRef;
import com.ktayl.core.screening.api.ScreeningGate;
import com.ktayl.core.screening.api.ScreeningOutcome;
import java.time.Instant;
import java.util.List;
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

    /** The bind gate marker threaded to the screening engine for audit attribution (RC-02 S2). */
    private static final String BIND_GATE = "bind";

    private final PolicyRepository policies;
    private final PolicyAuditLog audit;
    private final ScreeningGate screening;

    public PolicyService(PolicyRepository policies, PolicyAuditLog audit, ScreeningGate screening) {
        this.policies = policies;
        this.audit = audit;
        this.screening = screening;
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

    /**
     * SUBMITTED → ACTIVE (the bind), gated by sanctions/AML screening (RC-02 S2). The holder is screened at
     * the {@code bind} gate BEFORE the transition; a {@code HOLD} verdict (match OR a fail-closed engine
     * outage) blocks the bind — {@link BindScreeningHoldException} is thrown and the policy stays SUBMITTED,
     * unsaved, with an auditable alert for the compliance officer. Only an ALLOW proceeds to activate + save.
     */
    @Transactional
    public Policy activate(UUID id, String actor) {
        Policy policy = require(id);
        ScreeningOutcome outcome = screening.screen(
                new PartyRef(policy.policyNumber(), policy.holderName(), null, null, null), BIND_GATE, actor);
        if ("HOLD".equals(outcome.verdict())) {
            throw new BindScreeningHoldException(policy.id(), outcome.alertId(), BIND_GATE);
        }
        return transition(id, actor, "ACTIVATE", null, Policy::activate);
    }

    @Transactional
    public Policy cancel(UUID id, String reason, String actor) {
        return transition(id, actor, "CANCEL", reason, (p, now) -> p.cancel(reason, now));
    }

    /**
     * Update a DRAFT policy's mutable details (BF-POL-04). Audited (action UPDATE) so a pre-activation edit
     * is traceable like a transition; the aggregate rejects the edit if the policy is past DRAFT (→409).
     */
    @Transactional
    public Policy update(UUID id, UpdatePolicyCommand cmd, String actor) {
        Policy policy = require(id);
        PolicyStatus from = policy.status();
        policy.update(cmd.holderName(), cmd.productCode(), cmd.effectiveDate(), cmd.expiryDate(), Instant.now());
        policies.save(policy);
        audit.record(policy.id(), "UPDATE", from, policy.status(), actor, null);
        return policy;
    }

    /** Keyset page (BF-POL-04). {@code status} + {@code afterCreatedAt}/{@code afterId} (decoded cursor) are nullable. */
    @Transactional(readOnly = true)
    public List<Policy> list(PolicyStatus status, Instant afterCreatedAt, UUID afterId, int limit) {
        return policies.list(status, afterCreatedAt, afterId, limit);
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
