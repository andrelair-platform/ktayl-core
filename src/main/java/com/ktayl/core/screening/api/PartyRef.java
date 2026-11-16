package com.ktayl.core.screening.api;

import java.time.LocalDate;

/**
 * Published DTO describing the party to screen — the cross-module / wire contract (ADR-005). Deliberately a
 * flat value with no internal domain type, so a caller (policy / billing at the gate, S2) depends only on
 * this. {@code externalRef} is the caller's stable key for the party (used for idempotency + status lookups).
 */
public record PartyRef(
        String externalRef,
        String name,
        LocalDate dob,
        String nationality,
        String residence) {
}
