package com.ktayl.core.claimsgl.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimLedgerOutboxRepository extends JpaRepository<ClaimLedgerOutboxEntity, UUID> {

    /** The next batch of unposted claims GL postings, oldest first (the drainer's work queue). */
    List<ClaimLedgerOutboxEntity> findByStatusOrderByCreatedAt(String status, Limit limit);

    /** DB-level idempotency: a redelivered event (same event id) never queues a duplicate JE. */
    boolean existsByReference(String reference);
}
