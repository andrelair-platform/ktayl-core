package com.ktayl.core.submission.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data CRUD for {@link SubmissionTriageEntity} — driven adapter detail, used only by {@link TriageLogAdapter}. */
interface SubmissionTriageJpaRepository extends JpaRepository<SubmissionTriageEntity, Long> {
}
