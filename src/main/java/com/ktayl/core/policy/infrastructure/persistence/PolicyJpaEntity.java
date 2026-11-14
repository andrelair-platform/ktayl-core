package com.ktayl.core.policy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** JPA persistence shape for a policy — the `policy.policy` table. Distinct from the domain aggregate (ADR-013). */
@Entity
@Table(name = "policy", schema = "policy")
public class PolicyJpaEntity {

    @Id
    private UUID id;

    @Column(name = "policy_number", nullable = false, unique = true)
    private String policyNumber;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(nullable = false)
    private String status;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PolicyJpaEntity() {} // JPA

    public PolicyJpaEntity(UUID id, String policyNumber, String holderName, String productCode, String status,
            LocalDate effectiveDate, LocalDate expiryDate, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.policyNumber = policyNumber;
        this.holderName = holderName;
        this.productCode = productCode;
        this.status = status;
        this.effectiveDate = effectiveDate;
        this.expiryDate = expiryDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public String getHolderName() { return holderName; }
    public String getProductCode() { return productCode; }
    public String getStatus() { return status; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
