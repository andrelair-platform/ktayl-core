package com.ktayl.core.billing.payment;

import com.ktayl.core.billing.persistence.AuditEntity;
import com.ktayl.core.billing.persistence.AuditRepository;
import com.ktayl.core.billing.persistence.InstallmentEntity;
import com.ktayl.core.billing.persistence.InstallmentRepository;
import com.ktayl.core.billing.persistence.InvoiceEntity;
import com.ktayl.core.billing.persistence.InvoiceRepository;
import com.ktayl.core.billing.persistence.PaymentEntity;
import com.ktayl.core.billing.persistence.PaymentRepository;
import com.ktayl.core.billing.persistence.WebhookEventEntity;
import com.ktayl.core.billing.persistence.WebhookEventRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures a payment on the signature-verified {@code payment_intent.succeeded} webhook (BILL-013b). The
 * whole effect commits in ONE transaction and the webhook event is marked processed only on commit:
 * payment → succeeded, installment → paid, invoice → settled (when all installments are paid), emit
 * {@link PaymentCaptured}, audit. Guarantees:
 * <ul>
 *   <li><b>idempotent</b> by {@code psp_event_id} — a replayed event is a no-op;</li>
 *   <li><b>never dropped</b> — a webhook that arrives before its payment record is PARKED (event stored,
 *       {@code processed_at} null) for later reconciliation, not discarded.</li>
 * </ul>
 * Signature verification is the controller's job; by the time we're here the event is trusted.
 */
@Service
public class PaymentCaptureService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCaptureService.class);
    private static final String ACTOR = "system:stripe-webhook";

    private final PaymentRepository payments;
    private final InstallmentRepository installments;
    private final InvoiceRepository invoices;
    private final AuditRepository audit;
    private final WebhookEventRepository webhookEvents;
    private final ApplicationEventPublisher events;

    public PaymentCaptureService(PaymentRepository payments, InstallmentRepository installments,
            InvoiceRepository invoices, AuditRepository audit, WebhookEventRepository webhookEvents,
            ApplicationEventPublisher events) {
        this.payments = payments;
        this.installments = installments;
        this.invoices = invoices;
        this.audit = audit;
        this.webhookEvents = webhookEvents;
        this.events = events;
    }

    /** Apply a verified payment_intent.succeeded. {@code pspEventId} is the idempotency key. */
    @Transactional
    public void capturePaymentSucceeded(String pspEventId, String eventType, String pspIntentId) {
        WebhookEventEntity event = webhookEvents.findById(pspEventId).orElse(null);
        if (event != null && event.getProcessedAt() != null) {
            log.debug("stripe event {} already processed — idempotent no-op", pspEventId);
            return;
        }
        if (event == null) {
            event = webhookEvents.save(new WebhookEventEntity(pspEventId, eventType, Instant.now()));
        }

        PaymentEntity payment = payments.findByPspIntentId(pspIntentId).orElse(null);
        if (payment == null) {
            // parked: the webhook beat its payment record. Keep the event (processed_at null) for a later
            // reconcile; do NOT drop it and do NOT fail (Stripe would retry forever).
            log.warn("stripe event {} (intent {}) parked — no payment record yet", pspEventId, pspIntentId);
            return;
        }
        if ("succeeded".equals(payment.getStatus())) {
            event.markProcessed(Instant.now());
            return; // the payment is already captured (a late duplicate) — mark the event done
        }

        Instant now = Instant.now();
        payment.markSucceeded(now);
        InstallmentEntity installment = installments.findById(payment.getInstallmentId()).orElseThrow();
        installment.markPaid(now);

        // settle the invoice once EVERY installment is paid
        InvoiceEntity invoice = invoices.findById(installment.getInvoiceId()).orElseThrow();
        boolean allPaid = installments.findByInvoiceIdOrderBySeq(invoice.getId()).stream()
                .allMatch(InstallmentEntity::isPaid);
        if (allPaid) {
            invoice.settle();
        }

        audit.save(new AuditEntity("payment", payment.getId().toString(), "PAYMENT_CAPTURED", ACTOR,
                "intent=" + pspIntentId + " installment=" + installment.getId()
                        + (allPaid ? " invoice=settled" : ""), now));
        events.publishEvent(new PaymentCaptured(payment.getId(), installment.getId(),
                payment.getAmountMinor(), payment.getCurrency()));
        event.markProcessed(now);
        log.info("captured payment {} (installment {} paid{})",
                payment.getId(), installment.getId(), allPaid ? ", invoice settled" : "");
    }
}
