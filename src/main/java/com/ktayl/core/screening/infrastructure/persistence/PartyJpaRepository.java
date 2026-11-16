package com.ktayl.core.screening.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data CRUD for {@link PartyEntity} — a driven adapter detail, used only by the persistence adapter. */
interface PartyJpaRepository extends JpaRepository<PartyEntity, UUID> {

    Optional<PartyEntity> findByExternalRef(String externalRef);
}
