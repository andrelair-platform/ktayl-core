package com.ktayl.core.billing.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByClientKey(String clientKey);

    Optional<PaymentEntity> findByPspIntentId(String pspIntentId);

    List<PaymentEntity> findByInstallmentId(UUID installmentId);

    boolean existsByInstallmentIdAndStatus(UUID installmentId, String status);
}
