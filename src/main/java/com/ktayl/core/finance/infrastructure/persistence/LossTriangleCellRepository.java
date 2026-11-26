package com.ktayl.core.finance.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface LossTriangleCellRepository extends JpaRepository<LossTriangleCellEntity, UUID> {

    /** All cells of one triangle (identified by portfolio + valuation date + basis). */
    List<LossTriangleCellEntity> findByPortfolioAndAsOfAndBasis(String portfolio, LocalDate asOf, String basis);

    /** Replace-on-load: clear the existing triangle before inserting the new cells (one value per cell). */
    @Transactional
    void deleteByPortfolioAndAsOfAndBasis(String portfolio, LocalDate asOf, String basis);
}
