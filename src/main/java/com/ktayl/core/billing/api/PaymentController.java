package com.ktayl.core.billing.api;

import com.ktayl.core.billing.payment.PaymentService;
import com.ktayl.core.billing.persistence.PaymentEntity;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment API (SSO-gated, Finance group). Initiating a payment is <b>eventually consistent</b>: it returns
 * {@code pending} immediately; the installment flips to {@code paid} only on the verified Stripe webhook
 * (BILL-013b). Idempotent by {@code client_key}.
 */
@RestController
@RequestMapping("/api/billing/payments")
@PreAuthorize("hasAuthority('GROUP_Finance')")
public class PaymentController {

    public record InitiateRequest(UUID installmentId, String clientKey) {}

    public record PaymentView(UUID paymentId, UUID installmentId, String pspIntentId, long amountMinor,
            String currency, String status) {}

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    public ResponseEntity<PaymentView> initiate(@RequestBody InitiateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        if (request == null || request.installmentId() == null) {
            throw new IllegalArgumentException("installment_id is required");
        }
        PaymentEntity payment = payments.initiate(request.installmentId(), request.clientKey(), actor(jwt));
        return ResponseEntity.accepted().body(view(payment)); // 202 — capture is async
    }

    private static String actor(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return username != null ? username : jwt.getSubject();
    }

    private static PaymentView view(PaymentEntity p) {
        return new PaymentView(p.getId(), p.getInstallmentId(), p.getPspIntentId(),
                p.getAmountMinor(), p.getCurrency(), p.getStatus());
    }
}
