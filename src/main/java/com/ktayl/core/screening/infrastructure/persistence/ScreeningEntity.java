package com.ktayl.core.screening.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA persistence shape for a screening event — the {@code screening.screening} table. */
@Entity
@Table(name = "screening", schema = "screening")
public class ScreeningEntity {

    @Id
    private UUID id;

    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    @Column(nullable = false)
    private String gate;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "dataset_version")
    private String datasetVersion;

    @Column(nullable = false)
    private String verdict;

    protected ScreeningEntity() {} // JPA

    public ScreeningEntity(UUID id, UUID partyId, String gate, Instant at, String datasetVersion, String verdict) {
        this.id = id;
        this.partyId = partyId;
        this.gate = gate;
        this.at = at;
        this.datasetVersion = datasetVersion;
        this.verdict = verdict;
    }

    public UUID getId() { return id; }
    public UUID getPartyId() { return partyId; }
    public String getGate() { return gate; }
    public Instant getAt() { return at; }
    public String getDatasetVersion() { return datasetVersion; }
    public String getVerdict() { return verdict; }
}
