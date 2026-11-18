package com.ktayl.core.finance.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AccountingPeriodJpaRepository extends JpaRepository<AccountingPeriodJpaEntity, String> {
}
