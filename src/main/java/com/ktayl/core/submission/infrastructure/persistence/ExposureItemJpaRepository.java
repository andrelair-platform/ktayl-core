package com.ktayl.core.submission.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data CRUD for {@link ExposureItemJpaEntity} — a driven adapter detail. */
interface ExposureItemJpaRepository extends JpaRepository<ExposureItemJpaEntity, UUID> {

    List<ExposureItemJpaEntity> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);
}
