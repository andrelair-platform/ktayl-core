package com.ktayl.core.finance.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PeriodCloseAuditJpaRepository extends JpaRepository<PeriodCloseAuditEntity, UUID> {

    List<PeriodCloseAuditEntity> findByPeriodOrderByAtAsc(String period);

    Optional<PeriodCloseAuditEntity> findFirstByPeriodAndActionOrderByAtDesc(String period, String action);
}
