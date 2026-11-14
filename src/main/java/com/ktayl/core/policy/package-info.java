/**
 * Policy module — the PAS (policy administration) registry folded into ktayl-core from
 * ktayl-policy-service (ADR-014, phased strangler per ADR-009). Owns the policy lifecycle
 * {@code DRAFT → SUBMITTED → ACTIVE → AMENDED / CANCELLED / EXPIRED / REJECTED} with a DORA-Art.9 audit
 * trail. Underwriting's bind drives create → submit → activate. Design carried forward from the PAS BMAD:
 * {@code docs/architecture/adr/ADR-015-policy-module-folded-pas.md} + {@code docs/specs/spec-policy-fold/SPEC.md}.
 *
 * <p>A CLOSED Spring Modulith module: other modules use only its published {@code api} (the
 * {@link com.ktayl.core.policy.api.PolicyQuery} in-process read interface) + events, never its internal
 * {@code domain}/{@code application}/{@code infrastructure}; it owns the {@code policy} Postgres schema and
 * may not read another module's tables (ADR-005/006). Internally hexagonal (ADR-013), dependencies inward.
 *
 * <p>Phase 1 = the bind-path core + audit. Coverages, premium-metadata, documents, the attestation PDF
 * (ACPR Art.L113-5) and CloudEvents are Phase 1b (SPEC).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Policy")
package com.ktayl.core.policy;
