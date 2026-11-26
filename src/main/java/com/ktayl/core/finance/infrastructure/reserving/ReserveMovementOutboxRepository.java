package com.ktayl.core.finance.infrastructure.reserving;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReserveMovementOutboxRepository extends JpaRepository<ReserveMovementOutboxEntity, UUID> {

    /** The next batch of unposted reserve-movement postings, oldest first (the drainer's work queue). */
    List<ReserveMovementOutboxEntity> findByStatusOrderByCreatedAt(String status, Limit limit);

    boolean existsByReference(String reference);
}
