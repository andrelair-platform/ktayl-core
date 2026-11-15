package com.ktayl.core.policy.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PolicyJpaRepository extends JpaRepository<PolicyJpaEntity, UUID> {
    Optional<PolicyJpaEntity> findByPolicyNumber(String policyNumber);

    /**
     * First page (BF-POL-04), ordered by {@code (createdAt, id)} ascending. A null {@code status} disables
     * the filter. {@code limit} via the {@link Pageable} page size. (Split from the after-cursor query so a
     * nullable timestamp parameter never appears in an {@code IS NULL} context — Postgres cannot infer its
     * type there: "could not determine data type of parameter".)
     */
    @Query("""
            SELECT e FROM PolicyJpaEntity e
            WHERE (:status IS NULL OR e.status = :status)
            ORDER BY e.createdAt ASC, e.id ASC
            """)
    List<PolicyJpaEntity> listFirstPage(@Param("status") String status, Pageable pageable);

    /** Page after the {@code (afterCreatedAt, afterId)} cursor — the timestamp is only ever a typed comparison. */
    @Query("""
            SELECT e FROM PolicyJpaEntity e
            WHERE (:status IS NULL OR e.status = :status)
              AND (e.createdAt > :afterCreatedAt OR (e.createdAt = :afterCreatedAt AND e.id > :afterId))
            ORDER BY e.createdAt ASC, e.id ASC
            """)
    List<PolicyJpaEntity> listAfter(@Param("status") String status,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") UUID afterId,
            Pageable pageable);
}
