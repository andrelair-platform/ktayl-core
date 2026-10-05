package com.ktayl.core.billing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** A bound policy's premium ingested from the UW bound-risk event (keyed by policy_number). */
@Entity
@Table(name = "ingested_policy")
public class IngestedPolicyEntity {

    @Id
    @Column(name = "policy_number")
    private String policyNumber;

    @Column(name = "premium_minor", nullable = false)
    private long premiumMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "submission_id")
    private String submissionId;

    @Column(name = "quote_id")
    private String quoteId;

    @Column(nullable = false)
    private String status;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected IngestedPolicyEntity() {} // JPA

    public IngestedPolicyEntity(String policyNumber, long premiumMinor, String currency, String productCode,
            LocalDate effectiveDate, LocalDate expiryDate, String submissionId, String quoteId,
            String status, Instant ingestedAt) {
        this.policyNumber = policyNumber;
        this.premiumMinor = premiumMinor;
        this.currency = currency;
        this.productCode = productCode;
        this.effectiveDate = effectiveDate;
        this.expiryDate = expiryDate;
        this.submissionId = submissionId;
        this.quoteId = quoteId;
        this.status = status;
        this.ingestedAt = ingestedAt;
    }

    public String getPolicyNumber() { return policyNumber; }
    public long getPremiumMinor() { return premiumMinor; }
    public String getCurrency() { return currency; }
    public String getProductCode() { return productCode; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public String getSubmissionId() { return submissionId; }
    public String getQuoteId() { return quoteId; }
    public String getStatus() { return status; }
    public Instant getIngestedAt() { return ingestedAt; }
}
