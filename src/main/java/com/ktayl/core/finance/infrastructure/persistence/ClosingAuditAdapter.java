package com.ktayl.core.finance.infrastructure.persistence;

import com.ktayl.core.finance.domain.ClosingAuditEntry;
import com.ktayl.core.finance.domain.ClosingAuditPort;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Outbound adapter for the append-only close/reopen audit (FINCLOSE-02). */
@Component
class ClosingAuditAdapter implements ClosingAuditPort {

    private final PeriodCloseAuditJpaRepository repository;

    ClosingAuditAdapter(PeriodCloseAuditJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(ClosingAuditEntry e) {
        repository.save(new PeriodCloseAuditEntity(UUID.randomUUID(), e.period().toString(), e.action().name(),
                e.actor(), e.at(), e.subledgerPostedMinor(), e.glPostedMinor(), e.driftMinor(), e.glStatus(),
                e.forced(), e.reason()));
    }

    @Override
    public List<ClosingAuditEntry> history(YearMonth period) {
        return repository.findByPeriodOrderByAtAsc(period.toString()).stream().map(ClosingAuditAdapter::toDomain).toList();
    }

    @Override
    public Optional<Instant> lastClosedAt(YearMonth period) {
        return repository.findFirstByPeriodAndActionOrderByAtDesc(period.toString(), "CLOSED")
                .map(PeriodCloseAuditEntity::getAt);
    }

    private static ClosingAuditEntry toDomain(PeriodCloseAuditEntity e) {
        return new ClosingAuditEntry(YearMonth.parse(e.getPeriod()),
                ClosingAuditEntry.Action.valueOf(e.getAction()), e.getActor(), e.getAt(),
                e.getSubledgerPostedMinor(), e.getGlPostedMinor(), e.getDriftMinor(), e.getGlStatus(),
                e.isForced(), e.getReason());
    }
}
