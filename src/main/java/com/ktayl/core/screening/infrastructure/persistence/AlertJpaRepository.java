package com.ktayl.core.screening.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data CRUD for {@link AlertEntity} — a driven adapter detail. */
interface AlertJpaRepository extends JpaRepository<AlertEntity, UUID> {

    /** The alerts of a screening, id-ordered — so an idempotent HOLD return carries its first alert id. */
    List<AlertEntity> findByScreeningIdOrderById(UUID screeningId);

    /**
     * Keyset page of alerts joined to their screening (for the party id) + optional case (for the status),
     * optionally filtered by case status + bucket, ordered by alert id ascending after the {@code afterId}
     * cursor. A null filter param disables that filter. Returns {@link AlertRow} projections, mapped to the
     * domain {@link com.ktayl.core.screening.domain.AlertView} by the adapter.
     *
     * <p>The {@code status} filter is applied in the adapter (post-fetch) so a nullable left-joined column is
     * never compared in SQL — kept simple + type-safe like the policy keyset split.
     */
    @Query("""
            SELECT new com.ktayl.core.screening.infrastructure.persistence.AlertRow(
                a.id, a.screeningId, s.partyId, a.bucket, a.score, a.matchedEntry, c.status)
            FROM AlertEntity a
            JOIN ScreeningEntity s ON s.id = a.screeningId
            LEFT JOIN CaseEntity c ON c.alertId = a.id
            WHERE (:bucket IS NULL OR a.bucket = :bucket)
              AND (:afterId IS NULL OR a.id > :afterId)
            ORDER BY a.id ASC
            """)
    List<AlertRow> listAlerts(@Param("bucket") String bucket, @Param("afterId") UUID afterId, Pageable pageable);

    /** One alert joined to its screening (party) + optional case — the single-alert read for a decision. */
    @Query("""
            SELECT new com.ktayl.core.screening.infrastructure.persistence.AlertRow(
                a.id, a.screeningId, s.partyId, a.bucket, a.score, a.matchedEntry, c.status)
            FROM AlertEntity a
            JOIN ScreeningEntity s ON s.id = a.screeningId
            LEFT JOIN CaseEntity c ON c.alertId = a.id
            WHERE a.id = :alertId
            """)
    List<AlertRow> findAlertRow(@Param("alertId") UUID alertId);
}
