package com.ktayl.core.screening.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** JPA persistence shape for a screened party — the {@code screening.party} table (schema-per-module, ADR-006). */
@Entity
@Table(name = "party", schema = "screening")
public class PartyEntity {

    @Id
    private UUID id;

    @Column(name = "external_ref")
    private String externalRef;

    @Column(nullable = false)
    private String name;

    @Column
    private LocalDate dob;

    @Column
    private String nationality;

    @Column
    private String residence;

    @Column
    private String ids;

    @Column(name = "ubo_of")
    private String uboOf;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PartyEntity() {} // JPA

    public PartyEntity(UUID id, String externalRef, String name, LocalDate dob, String nationality,
            String residence, String ids, String uboOf, Instant createdAt) {
        this.id = id;
        this.externalRef = externalRef;
        this.name = name;
        this.dob = dob;
        this.nationality = nationality;
        this.residence = residence;
        this.ids = ids;
        this.uboOf = uboOf;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getExternalRef() { return externalRef; }
    public String getName() { return name; }
    public LocalDate getDob() { return dob; }
    public String getNationality() { return nationality; }
    public String getResidence() { return residence; }
    public String getIds() { return ids; }
    public String getUboOf() { return uboOf; }
    public Instant getCreatedAt() { return createdAt; }
}
