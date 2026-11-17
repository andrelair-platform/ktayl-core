package com.ktayl.core.submission.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model for an exposure item — an infrastructure adapter type, NOT the domain value (ADR-013).
 * Plain columns, no behaviour. Explicit {@code schema = "submission"} (schema-per-module, ADR-006).
 */
@Entity
@Table(name = "exposure_item", schema = "submission")
public class ExposureItemJpaEntity {

    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "site_name", nullable = false)
    private String siteName;

    @Column(nullable = false)
    private String country;

    @Column(name = "tiv_amount", nullable = false)
    private BigDecimal tivAmount;

    @Column(nullable = false)
    private String currency;

    @Column
    private String occupancy;

    @Column(name = "loss_history")
    private String lossHistory;

    @Column(name = "cat_flag", nullable = false)
    private boolean catFlag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExposureItemJpaEntity() {} // JPA

    public ExposureItemJpaEntity(UUID id, UUID submissionId, String siteName, String country,
            BigDecimal tivAmount, String currency, String occupancy, String lossHistory, boolean catFlag,
            Instant createdAt) {
        this.id = id;
        this.submissionId = submissionId;
        this.siteName = siteName;
        this.country = country;
        this.tivAmount = tivAmount;
        this.currency = currency;
        this.occupancy = occupancy;
        this.lossHistory = lossHistory;
        this.catFlag = catFlag;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public String getSiteName() { return siteName; }
    public String getCountry() { return country; }
    public BigDecimal getTivAmount() { return tivAmount; }
    public String getCurrency() { return currency; }
    public String getOccupancy() { return occupancy; }
    public String getLossHistory() { return lossHistory; }
    public boolean isCatFlag() { return catFlag; }
    public Instant getCreatedAt() { return createdAt; }
}
