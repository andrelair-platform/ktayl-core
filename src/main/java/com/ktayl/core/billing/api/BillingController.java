package com.ktayl.core.billing.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Billing API surface (SSO-gated). BILL-010 ships only a ping so the module, security chain and
 * routing are exercised end-to-end; the invoice/installment/payment endpoints arrive in BILL-012+.
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("module", "billing", "status", "ok", "at", Instant.now().toString());
    }
}
