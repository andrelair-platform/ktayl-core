package com.ktayl.core.billing.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {
    Optional<InvoiceEntity> findByPolicyNumber(String policyNumber);
    boolean existsByPolicyNumber(String policyNumber);
}
