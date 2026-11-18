package com.ktayl.core.finance.api;

/**
 * Published read model (FINCLOSE-01): the period premium register. Money is minor units (eurocents).
 * {@code receivableOutstandingMinor} = {@code grossPremiumInvoicedMinor} − {@code cashReceivedMinor}, scoped
 * to postings recorded <em>in this period</em> (not a cumulative aged-receivable — that is a later story), so
 * it can be negative when a period collects more than it invoices (cash for a prior period's invoice).
 */
public record PremiumRegister(
        String period,
        long grossPremiumInvoicedMinor,
        long cashReceivedMinor,
        long receivableOutstandingMinor,
        String currency) {
}
