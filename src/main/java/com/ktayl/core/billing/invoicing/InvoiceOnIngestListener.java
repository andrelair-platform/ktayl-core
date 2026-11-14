package com.ktayl.core.billing.invoicing;

import com.ktayl.core.billing.ingest.PolicyIngested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Auto-chain (BILL-012): when a bound policy is ingested, raise its invoice — but only **after the ingest
 * transaction commits** (AFTER_COMMIT), in the invoice's own transaction, so an invoicing failure never
 * rolls back the ingest (best-effort). The in-process event decouples ingest from invoicing within the
 * billing module. The API POST remains the manual/retry path (also idempotent).
 *
 * <p>Transaction note: {@code REQUIRES_NEW} is REQUIRED on an AFTER_COMMIT listener. The ingest
 * transaction is already committed when this fires, so a {@code REQUIRED} call (InvoiceService.raiseFor)
 * would join the completing synchronization and its writes would be executed but NEVER committed (observed
 * in prod 2026-11-14: InvoiceService logged "invoiced …" yet no invoice row persisted). REQUIRES_NEW opens
 * a genuinely new transaction so the invoice + installments commit on their own — making the javadoc above
 * ("in the invoice's own transaction") actually true.
 */
@Component
public class InvoiceOnIngestListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceOnIngestListener.class);
    private static final int DEFAULT_INSTALLMENTS = 1; // MVP: single annual premium

    private final InvoiceService invoices;

    public InvoiceOnIngestListener(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPolicyIngested(PolicyIngested event) {
        try {
            invoices.raiseFor(event.policyNumber(), DEFAULT_INSTALLMENTS);
        } catch (Exception e) {
            // best-effort: the ingest already committed; surface + let the API/retry re-raise.
            log.error("auto-invoice failed for {} (retry via API): {}", event.policyNumber(), e.getMessage());
        }
    }
}
