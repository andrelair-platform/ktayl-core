package com.ktayl.core.finance.infrastructure.persistence;

import com.ktayl.core.finance.domain.PeriodLifecyclePort;
import java.time.Instant;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

/** Outbound adapter that upserts a period's lifecycle status in the registry (FINCLOSE-02). */
@Component
class PeriodLifecycleAdapter implements PeriodLifecyclePort {

    private final AccountingPeriodJpaRepository repository;

    PeriodLifecycleAdapter(AccountingPeriodJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void close(YearMonth period) {
        upsert(period, "closed");
    }

    @Override
    public void reopen(YearMonth period) {
        upsert(period, "open");
    }

    private void upsert(YearMonth period, String status) {
        AccountingPeriodJpaEntity existing = repository.findById(period.toString()).orElse(null);
        if (existing != null) {
            existing.setStatus(status);
            repository.save(existing);
        } else {
            repository.save(new AccountingPeriodJpaEntity(period.toString(), status, Instant.now()));
        }
    }
}
