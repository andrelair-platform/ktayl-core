/**
 * The submission module's PUBLISHED surface (ADR-005) — the only package other modules may depend on. A
 * Spring Modulith {@link org.springframework.modulith.NamedInterface named interface} so the boundary is
 * enforced: cross-module callers (Distribution / underwriting) use
 * {@link com.ktayl.core.submission.api.SubmissionIntake} + {@link com.ktayl.core.submission.api.SubmissionQuery}
 * with their api-local DTOs, never the internal domain/application/infrastructure types. The contract is
 * expressed in java/primitive types + api-local records (status as a String), so the internal domain enums
 * can evolve without breaking consumers.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ktayl.core.submission.api;
