package com.ktayl.core.submission.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data CRUD + keyset list for {@link SubmissionJpaEntity} — a driven adapter detail. */
interface SubmissionJpaRepository extends JpaRepository<SubmissionJpaEntity, UUID> {

    /**
     * First page, ordered by {@code (receivedAt, id)} ascending. A null {@code status} disables the filter.
     * (Split from the after-cursor query so a nullable timestamp parameter never appears in an {@code IS NULL}
     * context — Postgres cannot infer its type there; mirrors the policy list.)
     */
    @Query("""
            SELECT e FROM SubmissionJpaEntity e
            WHERE (:status IS NULL OR e.status = :status)
            ORDER BY e.receivedAt ASC, e.id ASC
            """)
    List<SubmissionJpaEntity> listFirstPage(@Param("status") String status, Pageable pageable);

    /** Page after the {@code (afterReceivedAt, afterId)} cursor — the timestamp is only ever a typed comparison. */
    @Query("""
            SELECT e FROM SubmissionJpaEntity e
            WHERE (:status IS NULL OR e.status = :status)
              AND (e.receivedAt > :afterReceivedAt OR (e.receivedAt = :afterReceivedAt AND e.id > :afterId))
            ORDER BY e.receivedAt ASC, e.id ASC
            """)
    List<SubmissionJpaEntity> listAfter(@Param("status") String status,
            @Param("afterReceivedAt") Instant afterReceivedAt,
            @Param("afterId") UUID afterId,
            Pageable pageable);
}
