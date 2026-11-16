package com.ktayl.core.screening.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data CRUD for {@link CaseEntity} — a driven adapter detail. */
interface CaseJpaRepository extends JpaRepository<CaseEntity, UUID> {

    Optional<CaseEntity> findByAlertId(UUID alertId);
}
