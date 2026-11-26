package com.ktayl.core.finance.infrastructure.persistence;

import com.ktayl.core.finance.domain.reserving.ReserveReviewState;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReserveReviewRepository extends JpaRepository<ReserveReviewEntity, UUID> {

    List<ReserveReviewEntity> findByPortfolioOrderByCreatedAtDesc(String portfolio);

    List<ReserveReviewEntity> findAllByOrderByCreatedAtDesc();

    /**
     * The most recently approved review for a portfolio — its {@code estimatedIbnrMinor} is the current booked
     * reserve position, against which the next approval's GL movement is computed.
     */
    Optional<ReserveReviewEntity> findFirstByPortfolioAndStateOrderByDecidedAtDesc(
            String portfolio, ReserveReviewState state);

    /** Idempotent re-submit guard: a PENDING review already open for this exact (portfolio, as_of, basis, method). */
    Optional<ReserveReviewEntity> findFirstByPortfolioAndAsOfAndBasisAndMethodAndState(
            String portfolio, LocalDate asOf, String basis, String method, ReserveReviewState state);
}
