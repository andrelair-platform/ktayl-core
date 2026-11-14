package com.ktayl.core.billing.payment;

import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Stripe adapter for {@link PaymentGatewayClient} (ADR-002, test mode, SEPA Direct Debit). Active ONLY when
 * {@code billing.stripe.secret-key} is set — same deterministic property gate as the NATS consumer, so dev
 * (blank key) runs with no gateway bean and {@code PaymentService} returns a clear 503 instead of failing
 * against an unconfigured PSP. {@code @ConditionalOnExpression} (NOT {@code @ConditionalOnBean}) avoids the
 * order-dependent condition that silently dropped the bound-risk consumer in prod (2026-11-14).
 *
 * <p>The idempotency-key is our {@code clientKey}: Stripe returns the SAME PaymentIntent for a repeated
 * create with that key, so a retried initiate never double-charges. Capture is webhook-driven (not here).
 */
@Component
@ConditionalOnExpression("'${billing.stripe.secret-key:}' != ''")
public class StripePaymentGateway implements PaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    private final StripeClient stripe;

    public StripePaymentGateway(@Value("${billing.stripe.secret-key}") String secretKey) {
        this.stripe = new StripeClient(secretKey);
    }

    @Override
    public PspIntent createSepaPaymentIntent(String clientKey, long amountMinor, String currency) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountMinor)
                    .setCurrency(currency.toLowerCase())
                    .addPaymentMethodType("sepa_debit")
                    .build();
            RequestOptions options = RequestOptions.builder().setIdempotencyKey(clientKey).build();
            PaymentIntent intent = stripe.paymentIntents().create(params, options);
            log.info("created Stripe PaymentIntent {} ({}) for {} {} eurocents",
                    intent.getId(), intent.getStatus(), amountMinor, currency);
            return new PspIntent(intent.getId(), intent.getStatus());
        } catch (Exception e) {
            // transient PSP failure — let PaymentService keep the payment `pending` and surface for retry
            throw new PaymentGatewayUnavailableException("Stripe PaymentIntent create failed: " + e.getMessage(), e);
        }
    }
}
