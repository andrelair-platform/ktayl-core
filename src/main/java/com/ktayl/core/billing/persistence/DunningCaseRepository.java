package com.ktayl.core.billing.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DunningCaseRepository extends JpaRepository<DunningCaseEntity, UUID> {
    Optional<DunningCaseEntity> findByInstallmentId(UUID installmentId);
}
