/**
 * The complaints module's PUBLISHED surface (ADR-005) — the only package other modules may depend on. A
 * Spring Modulith {@link org.springframework.modulith.NamedInterface named interface} so the boundary is
 * enforced: cross-module callers use {@link com.ktayl.core.complaints.api.ComplaintOperations} +
 * {@link com.ktayl.core.complaints.api.ComplaintSummary}, never the internal domain/application/
 * infrastructure types. The contract is expressed in java/primitive types + api-local DTOs (channel/status
 * as Strings), so the internal domain enums can evolve without breaking consumers.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ktayl.core.complaints.api;
