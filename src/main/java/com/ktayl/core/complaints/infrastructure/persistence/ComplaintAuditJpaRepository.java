package com.ktayl.core.complaints.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data CRUD for {@link ComplaintAuditEntity} — driven adapter detail, used only by {@link AuditTrailAdapter}. */
interface ComplaintAuditJpaRepository extends JpaRepository<ComplaintAuditEntity, Long> {
}
