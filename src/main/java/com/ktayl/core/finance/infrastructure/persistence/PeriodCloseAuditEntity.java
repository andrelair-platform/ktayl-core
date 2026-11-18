package com.ktayl.core.finance.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model for one immutable close/reopen audit entry (FINCLOSE-02) — an infrastructure type, not a
 * domain value (ADR-013). Append-only: rows are inserted, never updated. Explicit {@code schema = "finance"}.
 */
@Entity
@Table(name = "period_close_audit", schema = "finance")
class PeriodCloseAuditEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String actor;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "subledger_posted_minor")
    private Long subledgerPostedMinor;

    @Column(name = "gl_posted_minor")
    private Long glPostedMinor;

    @Column(name = "drift_minor")
    private Long driftMinor;

    @Column(name = "gl_status")
    private String glStatus;

    @Column(nullable = false)
    private boolean forced;

    @Column
    private String reason;

    protected PeriodCloseAuditEntity() {} // JPA

    PeriodCloseAuditEntity(UUID id, String period, String action, String actor, Instant at,
            Long subledgerPostedMinor, Long glPostedMinor, Long driftMinor, String glStatus, boolean forced,
            String reason) {
        this.id = id;
        this.period = period;
        this.action = action;
        this.actor = actor;
        this.at = at;
        this.subledgerPostedMinor = subledgerPostedMinor;
        this.glPostedMinor = glPostedMinor;
        this.driftMinor = driftMinor;
        this.glStatus = glStatus;
        this.forced = forced;
        this.reason = reason;
    }

    String getPeriod() { return period; }
    String getAction() { return action; }
    String getActor() { return actor; }
    Instant getAt() { return at; }
    Long getSubledgerPostedMinor() { return subledgerPostedMinor; }
    Long getGlPostedMinor() { return glPostedMinor; }
    Long getDriftMinor() { return driftMinor; }
    String getGlStatus() { return glStatus; }
    boolean isForced() { return forced; }
    String getReason() { return reason; }
}
