package com.ktayl.core.screening.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A party (natural or legal person) put through screening — a pure domain value. The {@code id} is assigned
 * by the application when a party is first persisted; a transient party (before persistence) carries a null
 * id. {@code ids} is an opaque JSON string of identifiers; {@code uboOf} a free-text UBO back-reference.
 */
public record Party(
        UUID id,
        String externalRef,
        String name,
        LocalDate dob,
        String nationality,
        String residence,
        String ids,
        String uboOf) {
}
