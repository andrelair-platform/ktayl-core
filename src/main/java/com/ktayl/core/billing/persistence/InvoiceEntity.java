package com.ktayl.core.billing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A premium invoice for a bound policy (one per policy_number). total_minor = Σ its installments. */
@Entity
@Table(name = "invoice")
public class InvoiceEntity {

    @Id
    private UUID id;

    @Column(name = "policy_number", nullable = false, unique = true)
    private String policyNumber;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(nullable = false)
    private String status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    protected InvoiceEntity() {} // JPA

    public InvoiceEntity(UUID id, String policyNumber, String currency, long totalMinor, String status, Instant issuedAt) {
        this.id = id;
        this.policyNumber = policyNumber;
        this.currency = currency;
        this.totalMinor = totalMinor;
        this.status = status;
        this.issuedAt = issuedAt;
    }

    public UUID getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public String getCurrency() { return currency; }
    public long getTotalMinor() { return totalMinor; }
    public String getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
}
