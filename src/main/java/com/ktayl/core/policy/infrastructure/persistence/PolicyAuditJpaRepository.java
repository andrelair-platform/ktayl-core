package com.ktayl.core.policy.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface PolicyAuditJpaRepository extends JpaRepository<PolicyAuditEntity, Long> {
}
