package com.ktayl.core.complaints.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data CRUD for {@link ComplaintJpaEntity} — a driven adapter detail, used only by the persistence adapter. */
interface ComplaintJpaRepository extends JpaRepository<ComplaintJpaEntity, UUID> {

    Optional<ComplaintJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
