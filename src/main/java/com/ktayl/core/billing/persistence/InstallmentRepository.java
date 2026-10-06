package com.ktayl.core.billing.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallmentRepository extends JpaRepository<InstallmentEntity, UUID> {
    List<InstallmentEntity> findByInvoiceIdOrderBySeq(UUID invoiceId);
}
