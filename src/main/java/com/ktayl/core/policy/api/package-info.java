/**
 * The policy module's PUBLISHED surface (ADR-005) — the only package other modules may depend on. A Spring
 * Modulith {@link org.springframework.modulith.NamedInterface named interface}: cross-module callers use
 * {@link com.ktayl.core.policy.api.PolicyQuery} + {@link com.ktayl.core.policy.api.PolicyView}, never the
 * internal domain/application/infrastructure types. This in-process read interface is the shared-kernel
 * value of folding the PAS in (ADR-014) — billing/claims read policy without a cross-service HTTP hop.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ktayl.core.policy.api;
