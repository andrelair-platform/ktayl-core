package com.ktayl.core.billing.invoicing;

import com.ktayl.core.billing.persistence.AuditEntity;
import com.ktayl.core.billing.persistence.AuditRepository;
import com.ktayl.core.billing.persistence.IngestedPolicyEntity;
import com.ktayl.core.billing.persistence.IngestedPolicyRepository;
import com.ktayl.core.billing.persistence.InstallmentEntity;
import com.ktayl.core.billing.persistence.InstallmentRepository;
import com.ktayl.core.billing.persistence.InvoiceEntity;
import com.ktayl.core.billing.persistence.InvoiceRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raises the premium invoice + installment schedule for an ingested bound policy (BILL-012).
 * **Idempotent per policy** (one invoice per policy_number — a bind replay never duplicates); the invoice
 * total always **reconciles to the sum of its installments** (via {@link InstallmentScheduler}); emits
 * {@link PremiumInvoiced} for the GL posting (BILL-014); audited.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final String ACTOR = "system:billing";

    /** The invoice + whether this call created it (false = idempotent hit). */
    public record RaiseResult(InvoiceEntity invoice, boolean created) {}

    private final InvoiceRepository invoices;
    private final InstallmentRepository installments;
    private final IngestedPolicyRepository ingested;
    private final AuditRepository audit;
    private final ApplicationEventPublisher events;
    private final CommissionSchedule commissions;

    public InvoiceService(InvoiceRepository invoices, InstallmentRepository installments,
            IngestedPolicyRepository ingested, AuditRepository audit, ApplicationEventPublisher events,
            CommissionSchedule commissions) {
        this.invoices = invoices;
        this.installments = installments;
        this.ingested = ingested;
        this.audit = audit;
        this.events = events;
        this.commissions = commissions;
    }

    /** Raise the invoice for a policy. {@code count} = number of installments (MVP default 1 = annual). */
    @Transactional
    public RaiseResult raiseFor(String policyNumber, int count) {
        var existing = invoices.findByPolicyNumber(policyNumber);
        if (existing.isPresent()) {
            log.debug("invoice for {} already exists — idempotent no-op", policyNumber);
            return new RaiseResult(existing.get(), false);
        }
        IngestedPolicyEntity p = ingested.findById(policyNumber)
                .orElseThrow(() -> new PolicyNotIngestedException(policyNumber));

        // The policyholder is billed/collected on GROSS; the split only drives the GL (net income vs commission payable).
        CommissionSplit split = commissions.split(p.getPremiumMinor(), p.getProductCode());
        var schedule = InstallmentScheduler.split(split.grossMinor(), count, p.getEffectiveDate(), p.getExpiryDate());
        var invoice = new InvoiceEntity(UUID.randomUUID(), policyNumber, p.getCurrency(), split.grossMinor(),
                split.commissionMinor(), split.netMinor(), split.bps(), "issued", Instant.now());
        invoices.save(invoice);
        for (var s : schedule) {
            installments.save(new InstallmentEntity(UUID.randomUUID(), invoice.getId(), s.seq(), s.dueDate(),
                    s.amountMinor(), "open"));
        }
        audit.save(new AuditEntity("invoice", invoice.getId().toString(), "INVOICE_ISSUED", ACTOR,
                "gross_minor=" + split.grossMinor() + " net_minor=" + split.netMinor()
                        + " commission_minor=" + split.commissionMinor() + " (" + split.bps() + "bps) "
                        + p.getCurrency() + " x" + count, Instant.now()));
        events.publishEvent(new PremiumInvoiced(policyNumber, invoice.getId(), split.grossMinor(),
                split.netMinor(), split.commissionMinor(), invoice.getCurrency()));
        log.info("invoiced {} → gross {} net {} commission {} ({}bps) {} in {} installment(s)", policyNumber,
                split.grossMinor(), split.netMinor(), split.commissionMinor(), split.bps(), p.getCurrency(), count);
        return new RaiseResult(invoice, true);
    }
}
