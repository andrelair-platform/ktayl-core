package com.ktayl.core.finance.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persistence model for an accounting period (FINCLOSE-01) — an infrastructure adapter type, not a domain
 * value (ADR-013). The registry of periods and their open/closed status; FINCLOSE-01 only reads it (the close
 * action is FINCLOSE-02). Explicit {@code schema = "finance"} (schema-per-module, ADR-006).
 */
@Entity
@Table(name = "accounting_period", schema = "finance")
class AccountingPeriodJpaEntity {

    @Id
    @Column(name = "period", length = 7)
    private String period; // 'YYYY-MM'

    @Column(nullable = false)
    private String status; // 'open' | 'closed'

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccountingPeriodJpaEntity() {} // JPA

    AccountingPeriodJpaEntity(String period, String status, Instant createdAt) {
        this.period = period;
        this.status = status;
        this.createdAt = createdAt;
    }

    void setStatus(String status) {
        this.status = status;
    }

    String getPeriod() {
        return period;
    }

    String getStatus() {
        return status;
    }
}
