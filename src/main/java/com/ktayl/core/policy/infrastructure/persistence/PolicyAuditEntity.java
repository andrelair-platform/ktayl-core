package com.ktayl.core.policy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit of a policy state transition (BF-POL-03, DORA Art.9 — carried forward from the PAS).
 * Every lifecycle transition writes one immutable row: who (actor), from→to, why (reason), when. Only INSERTed.
 */
@Entity
@Table(name = "policy_audit_log", schema = "policy")
public class PolicyAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(nullable = false)
    private String action;

    @Column(name = "from_status", nullable = false)
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column
    private String reason;

    @Column(name = "at", nullable = false)
    private Instant at;

    protected PolicyAuditEntity() {} // JPA

    public PolicyAuditEntity(UUID policyId, String action, String fromStatus, String toStatus,
            String actorId, String reason, Instant at) {
        this.policyId = policyId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.reason = reason;
        this.at = at;
    }

    public Long getId() { return id; }
    public UUID getPolicyId() { return policyId; }
    public String getAction() { return action; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getActorId() { return actorId; }
    public String getReason() { return reason; }
    public Instant getAt() { return at; }
}
