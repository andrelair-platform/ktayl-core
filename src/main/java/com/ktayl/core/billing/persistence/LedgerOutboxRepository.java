package com.ktayl.core.billing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerOutboxRepository extends JpaRepository<LedgerOutboxEntity, UUID> {

    /** The next batch of unposted GL postings, oldest first (the drainer's work queue). */
    List<LedgerOutboxEntity> findByStatusOrderByCreatedAt(String status, Limit limit);

    boolean existsByEventTypeAndReference(String eventType, String reference);

    /**
     * Every posting whose {@code created_at} falls in the half-open window {@code [start, end)} — the
     * accounting-period slice the ledger reconciliation view aggregates (FINCLOSE-01). Half-open so a
     * posting at a month boundary belongs to exactly one period.
     */
    List<LedgerOutboxEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant start, Instant end);

    /**
     * Count postings in the period window {@code [start, end)} that were marked {@code posted} with a
     * {@code posted_at} strictly after {@code after} — the post-close-movement check (FINCLOSE-02).
     */
    @Query("""
            select count(e) from LedgerOutboxEntity e
            where e.createdAt >= :start and e.createdAt < :end
              and e.status = 'posted' and e.postedAt > :after
            """)
    long countPostedAfter(Instant start, Instant end, Instant after);
}
