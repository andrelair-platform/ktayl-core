package com.ktayl.core.billing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Append-only audit of a billing state change (BILL-011). Only ever INSERTed. */
@Entity
@Table(name = "audit_log")
public class AuditEntity {

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

    protected AuditEntity() {} // JPA

    public AuditEntity(String entity, String entityId, String action, String actor, String detail, Instant at) {
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
