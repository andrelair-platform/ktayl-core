package com.ktayl.core.screening.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only LCB-FT / DORA audit entry for the screening module (per-schema audit, ADR-005/010). Only ever
 * INSERTed; kept >= 5 years. Explicit {@code schema = "screening"} (schema-per-module, ADR-006).
 */
@Entity
@Table(name = "screening_audit", schema = "screening")
public class ScreeningAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_id")
    private UUID partyId;

    @Column(nullable = false)
    private String action;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "dataset_version")
    private String datasetVersion;

    @Column
    private String detail;

    @Column(name = "at", nullable = false)
    private Instant at;

    protected ScreeningAuditEntity() {} // JPA

    public ScreeningAuditEntity(UUID partyId, String action, String actorId, String datasetVersion,
            String detail, Instant at) {
        this.partyId = partyId;
        this.action = action;
        this.actorId = actorId;
        this.datasetVersion = datasetVersion;
        this.detail = detail;
        this.at = at;
    }

    public Long getId() { return id; }
    public UUID getPartyId() { return partyId; }
    public String getAction() { return action; }
    public String getActorId() { return actorId; }
    public String getDatasetVersion() { return datasetVersion; }
    public String getDetail() { return detail; }
    public Instant getAt() { return at; }
}
