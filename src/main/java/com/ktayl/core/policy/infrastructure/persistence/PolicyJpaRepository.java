package com.ktayl.core.policy.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PolicyJpaRepository extends JpaRepository<PolicyJpaEntity, UUID> {
    Optional<PolicyJpaEntity> findByPolicyNumber(String policyNumber);
}
