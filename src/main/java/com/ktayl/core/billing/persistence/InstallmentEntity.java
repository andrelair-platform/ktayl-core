package com.ktayl.core.billing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One scheduled installment of an invoice (Σ amount_minor over an invoice's rows = invoice.total_minor). */
@Entity
@Table(name = "installment")
public class InstallmentEntity {

    @Id
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(nullable = false)
    private int seq;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String status;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected InstallmentEntity() {} // JPA

    public InstallmentEntity(UUID id, UUID invoiceId, int seq, LocalDate dueDate, long amountMinor, String status) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.seq = seq;
        this.dueDate = dueDate;
        this.amountMinor = amountMinor;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public int getSeq() { return seq; }
    public LocalDate getDueDate() { return dueDate; }
    public long getAmountMinor() { return amountMinor; }
    public String getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
}
