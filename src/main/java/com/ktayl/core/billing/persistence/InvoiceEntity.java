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

    /** The gross premium the policyholder owes — equals {@link #totalMinor} (and Σ installments). */
    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinor;

    /** The intermediary's (broker) cut of the gross premium (BILL-012b). */
    @Column(name = "commission_minor", nullable = false)
    private long commissionMinor;

    /** gross − commission = what the insurer actually earns. */
    @Column(name = "net_minor", nullable = false)
    private long netMinor;

    /** Applied commission rate in basis points (1500 = 15%). */
    @Column(name = "commission_bps", nullable = false)
    private int commissionBps;

    @Column(nullable = false)
    private String status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    protected InvoiceEntity() {} // JPA

    /** Full constructor with the gross/net/commission split (BILL-012b). gross == total_minor. */
    public InvoiceEntity(UUID id, String policyNumber, String currency, long grossMinor, long commissionMinor,
            long netMinor, int commissionBps, String status, Instant issuedAt) {
        this.id = id;
        this.policyNumber = policyNumber;
        this.currency = currency;
        this.totalMinor = grossMinor;
        this.grossMinor = grossMinor;
        this.commissionMinor = commissionMinor;
        this.netMinor = netMinor;
        this.commissionBps = commissionBps;
        this.status = status;
        this.issuedAt = issuedAt;
    }

    /** Convenience: an invoice with no commission (net == gross) — pre-split behaviour. */
    public InvoiceEntity(UUID id, String policyNumber, String currency, long totalMinor, String status, Instant issuedAt) {
        this(id, policyNumber, currency, totalMinor, 0L, totalMinor, 0, status, issuedAt);
    }

    /** Settle the invoice once every installment is paid (BILL-013b): issued → settled. */
    public void settle() {
        this.status = "settled";
    }

    public UUID getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public String getCurrency() { return currency; }
    public long getTotalMinor() { return totalMinor; }
    public long getGrossMinor() { return grossMinor; }
    public long getCommissionMinor() { return commissionMinor; }
    public long getNetMinor() { return netMinor; }
    public int getCommissionBps() { return commissionBps; }
    public String getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
}
