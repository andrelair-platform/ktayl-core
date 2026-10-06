package com.ktayl.core.billing.invoicing;

import java.util.UUID;

/**
 * Domain event — a premium invoice was raised. BILL-014's GL posting (the outbox) listens for this to
 * write the `DR Premium Receivable / CR Premium Income` Journal Entry. In-process (Spring application
 * event) within the billing module today; a candidate to externalise if invoicing ever extracts.
 */
public record PremiumInvoiced(String policyNumber, UUID invoiceId, long totalMinor, String currency) {}
