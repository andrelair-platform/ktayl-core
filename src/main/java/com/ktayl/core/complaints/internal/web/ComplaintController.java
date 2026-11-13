package com.ktayl.core.complaints.internal.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Complaints API surface (SSO-gated by the shared {@code SecurityConfig} — {@code /api/**} requires a
 * valid Authentik token). CMPL-010 ships only a ping so the module, the security chain and routing are
 * exercised end-to-end; the register/acknowledge/assign/resolve/close endpoints + the published
 * {@code ComplaintOperations}/{@code ComplaintSummary} API arrive in CMPL-011..013.
 *
 * <p>Inbound HTTP adapter — lives in {@code internal/web} (ADR-005 layout: {@code api} is reserved for
 * the surface other modules consume; a REST controller is an entry point, not a cross-module contract).
 */
@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("module", "complaints", "status", "ok", "at", Instant.now().toString());
    }
}
