package com.ktayl.core.billing.invoicing;

import java.util.UUID;

/**
 * Domain event — a premium invoice was raised. BILL-014's GL posting (the outbox) listens for this to
 * write the balanced Journal Entry: {@code DR Premium Receivable (gross) / CR Premium Income (net) /
 * CR Commission Payable (commission)} (BILL-012b — the split; the commission leg is omitted when zero).
 * In-process (Spring application event) within the billing module today; a candidate to externalise if
 * invoicing ever extracts.
 *
 * @param totalMinor      the GROSS premium (what the policyholder owes) — kept as {@code totalMinor} for
 *                        backward compatibility; the GL receivable + the premium register key off this
 * @param netMinor        gross − commission (what the insurer earns)
 * @param commissionMinor the intermediary's cut
 */
public record PremiumInvoiced(String policyNumber, UUID invoiceId, long totalMinor, long netMinor,
        long commissionMinor, String currency) {}
