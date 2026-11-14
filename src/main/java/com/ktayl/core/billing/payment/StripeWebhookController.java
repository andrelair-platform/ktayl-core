package com.ktayl.core.billing.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Stripe M2M webhook (BILL-013b) — NOT SSO-gated (Stripe has no OIDC token); the security gate is
 * {@link Webhook#constructEvent} verifying the {@code Stripe-Signature} against the signing secret. Only a
 * signature-valid payload is acted on. On {@code payment_intent.succeeded} it hands off to
 * {@link PaymentCaptureService} (idempotent by event id). Any other event type is acknowledged and ignored.
 *
 * <p>Status contract: {@code 400} bad/forged signature (ignored) · {@code 503} not configured (no secret) ·
 * {@code 200} accepted (incl. a parked/duplicate no-op). We return 2xx for handled events so Stripe does not
 * retry a correctly-received webhook.
 */
@RestController
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
    private static final String PAYMENT_SUCCEEDED = "payment_intent.succeeded";

    private final String webhookSecret;
    private final PaymentCaptureService capture;
    private final ObjectMapper mapper = new ObjectMapper();

    public StripeWebhookController(@Value("${billing.stripe.webhook-secret:}") String webhookSecret,
            PaymentCaptureService capture) {
        this.webhookSecret = webhookSecret;
        this.capture = capture;
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Map<String, Object>> handle(@RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", 503, "detail", "stripe webhook not configured"));
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret); // verifies the signature
        } catch (SignatureVerificationException e) {
            log.warn("rejected Stripe webhook — bad signature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", 400, "detail", "invalid signature"));
        }

        if (!PAYMENT_SUCCEEDED.equals(event.getType())) {
            return ResponseEntity.ok(Map.of("status", 200, "ignored", event.getType()));
        }
        String intentId = intentIdOf(payload);
        capture.capturePaymentSucceeded(event.getId(), event.getType(), intentId);
        return ResponseEntity.ok(Map.of("status", 200, "event", event.getId()));
    }

    /** The PaymentIntent id = {@code data.object.id}; parsed from the raw JSON (version-independent). */
    private String intentIdOf(String payload) {
        try {
            return mapper.readTree(payload).path("data").path("object").path("id").asText();
        } catch (Exception e) {
            throw new IllegalArgumentException("malformed Stripe event payload", e);
        }
    }
}
