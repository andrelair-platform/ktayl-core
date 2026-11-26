package com.ktayl.core.finance.infrastructure.persistence;

import com.ktayl.core.finance.domain.reserving.ReserveReviewNotPendingException;
import com.ktayl.core.finance.domain.reserving.ReserveReviewState;
import com.ktayl.core.finance.domain.reserving.SelfApprovalException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A maker-checker reserve review (FIN-03). Submitted PENDING by a maker with a snapshot of the IBNR estimate;
 * an independent checker approves or rejects it. The <b>segregation-of-duties</b> rule (threat T7) and the
 * must-be-PENDING guard live on this entity's {@link #approve}/{@link #reject} methods, so the control is a
 * pure, unit-testable invariant — a checker can never be the maker, and a terminal review can never be decided
 * again. On approval the signed IBNR movement (vs the last approved position) is recorded here and enqueued to
 * the GL outbox by the service.
 */
@Entity
@Table(name = "reserve_review", schema = "finance")
public class ReserveReviewEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String portfolio;

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Column(nullable = false)
    private String basis;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "latest_minor", nullable = false)
    private long latestMinor;

    @Column(name = "estimated_ultimate_minor", nullable = false)
    private long estimatedUltimateMinor;

    @Column(name = "estimated_ibnr_minor", nullable = false)
    private long estimatedIbnrMinor;

    @Column(name = "detail_json", nullable = false)
    private String detailJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReserveReviewState state;

    @Column(nullable = false)
    private String maker;

    @Column
    private String checker;

    @Column(name = "booked_movement_minor")
    private Long bookedMovementMinor;

    @Column
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected ReserveReviewEntity() {} // JPA

    public ReserveReviewEntity(UUID id, String portfolio, LocalDate asOf, String basis, String method,
            String currency, long latestMinor, long estimatedUltimateMinor, long estimatedIbnrMinor,
            String detailJson, String maker, Instant createdAt) {
        this.id = id;
        this.portfolio = portfolio;
        this.asOf = asOf;
        this.basis = basis;
        this.method = method;
        this.currency = currency;
        this.latestMinor = latestMinor;
        this.estimatedUltimateMinor = estimatedUltimateMinor;
        this.estimatedIbnrMinor = estimatedIbnrMinor;
        this.detailJson = detailJson;
        this.maker = maker;
        this.state = ReserveReviewState.PENDING;
        this.createdAt = createdAt;
    }

    /**
     * Approve the review (four-eyes). The signed {@code movementMinor} (new IBNR − last approved position) is
     * recorded for the GL booking. Enforces: must be PENDING, and the checker must differ from the maker.
     */
    public void approve(String checker, long movementMinor, String note, Instant at) {
        guardDecidable(checker);
        this.state = ReserveReviewState.APPROVED;
        this.checker = checker;
        this.bookedMovementMinor = movementMinor;
        this.reason = note;
        this.decidedAt = at;
    }

    /** Reject the review (four-eyes) with a reason. Enforces: must be PENDING, and checker ≠ maker. */
    public void reject(String checker, String reason, Instant at) {
        guardDecidable(checker);
        this.state = ReserveReviewState.REJECTED;
        this.checker = checker;
        this.reason = reason;
        this.decidedAt = at;
    }

    private void guardDecidable(String checker) {
        if (state != ReserveReviewState.PENDING) {
            throw new ReserveReviewNotPendingException(id, state);
        }
        if (maker.equalsIgnoreCase(checker)) {
            throw new SelfApprovalException(checker);
        }
    }

    public UUID getId() { return id; }
    public String getPortfolio() { return portfolio; }
    public LocalDate getAsOf() { return asOf; }
    public String getBasis() { return basis; }
    public String getMethod() { return method; }
    public String getCurrency() { return currency; }
    public long getLatestMinor() { return latestMinor; }
    public long getEstimatedUltimateMinor() { return estimatedUltimateMinor; }
    public long getEstimatedIbnrMinor() { return estimatedIbnrMinor; }
    public String getDetailJson() { return detailJson; }
    public ReserveReviewState getState() { return state; }
    public String getMaker() { return maker; }
    public String getChecker() { return checker; }
    public Long getBookedMovementMinor() { return bookedMovementMinor; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
