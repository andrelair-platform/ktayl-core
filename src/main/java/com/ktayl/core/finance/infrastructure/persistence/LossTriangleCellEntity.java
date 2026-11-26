package com.ktayl.core.finance.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One cumulative cell of a stored loss triangle (FIN-03). A triangle is the set of cells sharing
 * {@code (portfolio, asOf, basis)}; the {@code application} layer reads them and builds a
 * {@link com.ktayl.core.finance.domain.reserving.LossTriangle} (which enforces the staircase shape).
 */
@Entity
@Table(name = "loss_triangle_cell", schema = "finance")
public class LossTriangleCellEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String portfolio;

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Column(nullable = false)
    private String basis;

    @Column(name = "accident_period", nullable = false)
    private String accidentPeriod;

    @Column(name = "development_lag", nullable = false)
    private int developmentLag;

    @Column(name = "cumulative_minor", nullable = false)
    private long cumulativeMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    protected LossTriangleCellEntity() {} // JPA

    public LossTriangleCellEntity(UUID id, String portfolio, LocalDate asOf, String basis, String accidentPeriod,
            int developmentLag, long cumulativeMinor, String currency, Instant createdAt, String createdBy) {
        this.id = id;
        this.portfolio = portfolio;
        this.asOf = asOf;
        this.basis = basis;
        this.accidentPeriod = accidentPeriod;
        this.developmentLag = developmentLag;
        this.cumulativeMinor = cumulativeMinor;
        this.currency = currency;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public String getPortfolio() { return portfolio; }
    public LocalDate getAsOf() { return asOf; }
    public String getBasis() { return basis; }
    public String getAccidentPeriod() { return accidentPeriod; }
    public int getDevelopmentLag() { return developmentLag; }
    public long getCumulativeMinor() { return cumulativeMinor; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
}
