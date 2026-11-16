package com.ktayl.core.screening.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data CRUD for {@link ScreeningEntity} — a driven adapter detail. */
interface ScreeningJpaRepository extends JpaRepository<ScreeningEntity, UUID> {

    /** All screenings for a party + gate within a day window, newest first (idempotency lookup). */
    @Query("""
            SELECT s FROM ScreeningEntity s
            WHERE s.partyId = :partyId AND s.gate = :gate
              AND s.at >= :dayStart AND s.at < :dayEnd
            ORDER BY s.at DESC
            """)
    List<ScreeningEntity> findForPartyGateDay(@Param("partyId") UUID partyId, @Param("gate") String gate,
            @Param("dayStart") Instant dayStart, @Param("dayEnd") Instant dayEnd, Pageable pageable);

    /** The latest screening for a party (its current status). */
    Optional<ScreeningEntity> findFirstByPartyIdOrderByAtDesc(UUID partyId);
}
