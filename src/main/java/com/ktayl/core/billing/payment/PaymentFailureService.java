package com.ktayl.core.billing.payment;

import com.ktayl.core.billing.persistence.AuditEntity;
import com.ktayl.core.billing.persistence.AuditRepository;
import com.ktayl.core.billing.persistence.DunningCaseEntity;
import com.ktayl.core.billing.persistence.DunningCaseRepository;
import com.ktayl.core.billing.persistence.InstallmentEntity;
import com.ktayl.core.billing.persistence.InstallmentRepository;
import com.ktayl.core.billing.persistence.InvoiceEntity;
import com.ktayl.core.billing.persistence.InvoiceRepository;
import com.ktayl.core.billing.persistence.PaymentEntity;
import com.ktayl.core.billing.persistence.PaymentRepository;
import com.ktayl.core.billing.persistence.WebhookEventEntity;
import com.ktayl.core.billing.persistence.WebhookEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles a failed premium collection on the signature-verified {@code payment_intent.payment_failed}
 * webhook (BILL-013c) — the non-payment path. In ONE transaction: payment → failed, the installment stays
 * OPEN, the dunning case for the installment is opened/incremented, and at the lapse threshold a
 * {@link PolicyLapseRecommended} is emitted (a recommendation for review, not an auto-cancellation).
 * Idempotent by {@code psp_event_id}; a webhook that beats its payment record is parked, not dropped.
 */
@Service
public class PaymentFailureService {

    private static final Logger log = LoggerFactory.getLogger(PaymentFailureService.class);
    private static final String ACTOR = "system:stripe-webhook";

    private final PaymentRepository payments;
    private final InstallmentRepository installments;
    private final InvoiceRepository invoices;
    private final DunningCaseRepository dunning;
    private final AuditRepository audit;
    private final WebhookEventRepository webhookEvents;
    private final ApplicationEventPublisher events;
    private final int lapseThreshold;

    public PaymentFailureService(PaymentRepository payments, InstallmentRepository installments,
            InvoiceRepository invoices, DunningCaseRepository dunning, AuditRepository audit,
            WebhookEventRepository webhookEvents, ApplicationEventPublisher events,
            @Value("${billing.dunning.lapse-threshold:3}") int lapseThreshold) {
        this.payments = payments;
        this.installments = installments;
        this.invoices = invoices;
        this.dunning = dunning;
        this.audit = audit;
        this.webhookEvents = webhookEvents;
        this.events = events;
        this.lapseThreshold = lapseThreshold;
    }

    @Transactional
    public void handlePaymentFailed(String pspEventId, String eventType, String pspIntentId, String reason) {
        WebhookEventEntity event = webhookEvents.findById(pspEventId).orElse(null);
        if (event != null && event.getProcessedAt() != null) {
            return; // idempotent replay
        }
        if (event == null) {
            event = webhookEvents.save(new WebhookEventEntity(pspEventId, eventType, Instant.now()));
        }

        PaymentEntity payment = payments.findByPspIntentId(pspIntentId).orElse(null);
        if (payment == null) {
            log.warn("stripe failure event {} (intent {}) parked — no payment record yet", pspEventId, pspIntentId);
            return; // parked, not dropped
        }

        Instant now = Instant.now();
        payment.markFailed(); // installment stays OPEN — the premium is still owed
        InstallmentEntity installment = installments.findById(payment.getInstallmentId()).orElseThrow();
        InvoiceEntity invoice = invoices.findById(installment.getInvoiceId()).orElseThrow();
        String policyNumber = invoice.getPolicyNumber();

        DunningCaseEntity dunningCase = dunning.findByInstallmentId(installment.getId())
                .orElseGet(() -> dunning.save(new DunningCaseEntity(UUID.randomUUID(), installment.getId(), policyNumber, now)));
        boolean reachedLapse = dunningCase.recordFailure(reason, lapseThreshold, now);

        audit.save(new AuditEntity("payment", payment.getId().toString(), "PAYMENT_FAILED", ACTOR,
                "intent=" + pspIntentId + " dunning_attempts=" + dunningCase.getAttempts()
                        + (reason != null ? " reason=" + reason : ""), now));
        events.publishEvent(new PaymentFailed(payment.getId(), installment.getId(), policyNumber, reason));
        if (reachedLapse) {
            audit.save(new AuditEntity("dunning", dunningCase.getId().toString(), "LAPSE_RECOMMENDED", ACTOR,
                    "policy=" + policyNumber + " attempts=" + dunningCase.getAttempts(), now));
            events.publishEvent(new PolicyLapseRecommended(policyNumber, installment.getId(), dunningCase.getAttempts(), reason));
            log.warn("dunning for {} reached lapse threshold ({} attempts) — LAPSE RECOMMENDED (human review)",
                    policyNumber, dunningCase.getAttempts());
        } else {
            log.info("payment failed for {} — dunning attempt {} (installment stays open)", policyNumber, dunningCase.getAttempts());
        }
        event.markProcessed(now);
    }
}
