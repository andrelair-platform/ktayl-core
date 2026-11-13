package com.ktayl.core.complaints.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Append-only audit entry for the complaints module (per-schema audit, ADR-005/010). Only ever INSERTed.
 * Explicit {@code schema = "complaints"} (schema-per-module, ADR-006).
 */
@Entity
@Table(name = "audit_log", schema = "complaints")
public class ComplaintAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String entity;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String actor;

    @Column
    private String detail;

    @Column(nullable = false)
    private Instant at;

    protected ComplaintAuditEntity() {} // JPA

    public ComplaintAuditEntity(String entity, String entityId, String action, String actor, String detail,
            Instant at) {
        this.entity = entity;
        this.entityId = entityId;
        this.action = action;
        this.actor = actor;
        this.detail = detail;
        this.at = at;
    }

    public Long getId() { return id; }
    public String getEntity() { return entity; }
    public String getEntityId() { return entityId; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public String getDetail() { return detail; }
    public Instant getAt() { return at; }
}
