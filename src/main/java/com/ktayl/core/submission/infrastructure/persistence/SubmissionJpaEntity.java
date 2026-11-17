package com.ktayl.core.submission.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model for a submission — an infrastructure adapter type, NOT the domain aggregate (ADR-013).
 * Deliberately dumb: plain columns (status as a String, mapped to the domain enum by
 * {@link SubmissionPersistenceAdapter}), no behaviour. Explicit {@code schema = "submission"} because the
 * global {@code default_schema} is billing (schema-per-module, ADR-006).
 */
@Entity
@Table(name = "submission", schema = "submission")
public class SubmissionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "broker_ref", nullable = false)
    private String brokerRef;

    @Column(name = "insured_name", nullable = false)
    private String insuredName;

    @Column(name = "product_line", nullable = false)
    private String productLine;

    @Column(nullable = false)
    private String status;

    @Column(name = "screening_hold", nullable = false)
    private boolean screeningHold;

    @Column(name = "screening_alert_id")
    private UUID screeningAlertId;

    @Column(name = "routed_target")
    private String routedTarget;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SubmissionJpaEntity() {} // JPA

    public SubmissionJpaEntity(UUID id, String brokerRef, String insuredName, String productLine, String status,
            boolean screeningHold, UUID screeningAlertId, String routedTarget, Instant receivedAt,
            Instant updatedAt) {
        this.id = id;
        this.brokerRef = brokerRef;
        this.insuredName = insuredName;
        this.productLine = productLine;
        this.status = status;
        this.screeningHold = screeningHold;
        this.screeningAlertId = screeningAlertId;
        this.routedTarget = routedTarget;
        this.receivedAt = receivedAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getBrokerRef() { return brokerRef; }
    public String getInsuredName() { return insuredName; }
    public String getProductLine() { return productLine; }
    public String getStatus() { return status; }
    public boolean isScreeningHold() { return screeningHold; }
    public UUID getScreeningAlertId() { return screeningAlertId; }
    public String getRoutedTarget() { return routedTarget; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
