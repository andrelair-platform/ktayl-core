package com.ktayl.core.screening.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data CRUD for {@link ScreeningAuditEntity} — driven adapter detail, used only by the audit adapter. */
interface ScreeningAuditJpaRepository extends JpaRepository<ScreeningAuditEntity, Long> {
}
