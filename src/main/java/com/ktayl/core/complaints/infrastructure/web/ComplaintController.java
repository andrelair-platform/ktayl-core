package com.ktayl.core.complaints.infrastructure.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound HTTP adapter for the complaints module (hexagonal, ADR-013) — SSO-gated by the shared
 * {@code SecurityConfig} ({@code /api/**} requires a valid Authentik token). CMPL-010 ships only a ping so
 * the module, the security chain and routing are exercised end-to-end; the {@code POST /api/complaints}
 * register endpoint (wiring the {@code Idempotency-Key} header onto {@code RegisterComplaint}) + the
 * published {@code ComplaintOperations}/{@code ComplaintSummary} API arrive in CMPL-013.
 *
 * <p>Lives in {@code infrastructure.web}: a REST controller is a driving adapter, not a cross-module
 * contract ({@code api} is reserved for the surface other modules consume).
 */
@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("module", "complaints", "status", "ok", "at", Instant.now().toString());
    }
}
