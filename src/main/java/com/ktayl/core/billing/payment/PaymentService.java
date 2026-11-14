package com.ktayl.core.billing.payment;

import com.ktayl.core.billing.persistence.AuditEntity;
import com.ktayl.core.billing.persistence.AuditRepository;
import com.ktayl.core.billing.persistence.InstallmentEntity;
import com.ktayl.core.billing.persistence.InstallmentRepository;
import com.ktayl.core.billing.persistence.InvoiceEntity;
import com.ktayl.core.billing.persistence.InvoiceRepository;
import com.ktayl.core.billing.persistence.PaymentEntity;
import com.ktayl.core.billing.persistence.PaymentRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initiates a premium payment for an installment (BILL-013a). Creates a SEPA-DD PaymentIntent via the PSP
 * port and persists {@code payment(pending)} — capture is the webhook's job (BILL-013b). Guarantees:
 * <ul>
 *   <li><b>idempotent by {@code clientKey}</b> — a repeated initiate returns the SAME payment (and Stripe
 *       returns the same intent for that idempotency-key), never a duplicate;</li>
 *   <li><b>no over-pay</b> — a paid installment is rejected with 409 BEFORE any PSP call;</li>
 *   <li><b>no partial state</b> — if the gateway is absent/unreachable it throws 503 and nothing persists.</li>
 * </ul>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final InstallmentRepository installments;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final AuditRepository audit;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<PaymentGatewayClient> gateway;

    public PaymentService(InstallmentRepository installments, InvoiceRepository invoices,
            PaymentRepository payments, AuditRepository audit, ApplicationEventPublisher events,
            ObjectProvider<PaymentGatewayClient> gateway) {
        this.installments = installments;
        this.invoices = invoices;
        this.payments = payments;
        this.audit = audit;
        this.events = events;
        this.gateway = gateway;
    }

    @Transactional
    public PaymentEntity initiate(UUID installmentId, String clientKey, String actor) {
        if (clientKey == null || clientKey.isBlank()) {
            throw new IllegalArgumentException("client_key is required");
        }
        // idempotent: same client_key → the same payment (safe retry, no duplicate intent)
        var existing = payments.findByClientKey(clientKey);
        if (existing.isPresent()) {
            log.debug("payment for client_key {} already initiated — idempotent no-op", clientKey);
            return existing.get();
        }

        InstallmentEntity installment = installments.findById(installmentId)
                .orElseThrow(() -> new InstallmentNotFoundException("no installment " + installmentId));
        if (installment.isPaid()) {
            throw new PaymentNotAllowedException("installment " + installmentId + " is already paid");
        }
        InvoiceEntity invoice = invoices.findById(installment.getInvoiceId())
                .orElseThrow(() -> new InstallmentNotFoundException("no invoice for installment " + installmentId));

        PaymentGatewayClient psp = gateway.getIfAvailable();
        if (psp == null) {
            throw new PaymentGatewayUnavailableException("payment gateway not configured (no Stripe key)");
        }

        long amount = installment.getAmountMinor();
        String currency = invoice.getCurrency();
        var intent = psp.createSepaPaymentIntent(clientKey, amount, currency);

        UUID paymentId = UUID.randomUUID();
        payments.save(new PaymentEntity(paymentId, installmentId, clientKey, intent.intentId(),
                amount, currency, "pending", Instant.now()));
        audit.save(new AuditEntity("payment", paymentId.toString(), "PAYMENT_INITIATED", actor,
                "intent=" + intent.intentId() + " " + amount + " " + currency, Instant.now()));
        events.publishEvent(new PaymentInitiated(paymentId, installmentId, clientKey, amount, currency));
        log.info("initiated payment {} for installment {} ({} {} eurocents, intent {})",
                paymentId, installmentId, amount, currency, intent.intentId());
        return payments.findById(paymentId).orElseThrow();
    }
}
