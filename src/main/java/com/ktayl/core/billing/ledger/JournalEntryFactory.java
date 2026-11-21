package com.ktayl.core.billing.ledger;

import com.ktayl.core.billing.ledger.JournalEntry.Line;
import com.ktayl.core.billing.persistence.LedgerOutboxEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the balanced double-entry for a GL outbox row (BILL-014). The account mapping is the only
 * domain knowledge here; the balance invariant is enforced by {@link JournalEntry} itself.
 * <ul>
 *   <li><b>PREMIUM_INVOICED</b> (the premium becomes a receivable): DR Premium Receivable (gross) /
 *       CR Premium Income (net) / CR Commission Payable (commission) — the BILL-012b split. The commission
 *       leg is omitted when the commission is 0, collapsing to the classic 2-leg DR receivable / CR income.</li>
 *   <li><b>PAYMENT_CAPTURED</b> (cash collected on the gross): DR Cash / CR Premium Receivable</li>
 * </ul>
 * Account names come from config so they map to the actual ERPNext chart of accounts without a code change.
 */
@Component
public class JournalEntryFactory {

    public static final String PREMIUM_INVOICED = "PREMIUM_INVOICED";
    public static final String PAYMENT_CAPTURED = "PAYMENT_CAPTURED";

    private final String premiumReceivable;
    private final String premiumIncome;
    private final String commissionPayable;
    private final String cash;

    public JournalEntryFactory(
            @Value("${ledger.erpnext.accounts.premium-receivable:Premium Receivable}") String premiumReceivable,
            @Value("${ledger.erpnext.accounts.premium-income:Premium Income}") String premiumIncome,
            @Value("${ledger.erpnext.accounts.commission-payable:Commission Payable}") String commissionPayable,
            @Value("${ledger.erpnext.accounts.cash:Cash}") String cash) {
        this.premiumReceivable = premiumReceivable;
        this.premiumIncome = premiumIncome;
        this.commissionPayable = commissionPayable;
        this.cash = cash;
    }

    public JournalEntry from(LedgerOutboxEntity row) {
        long gross = row.getAmountMinor();
        long commission = row.getCommissionMinor();
        String cur = row.getCurrency();
        String remark = row.getEventType() + " " + row.getReference();
        return switch (row.getEventType()) {
            case PREMIUM_INVOICED -> {
                List<Line> lines = new ArrayList<>(3);
                lines.add(new Line(premiumReceivable, gross, 0));          // DR receivable: gross
                lines.add(new Line(premiumIncome, 0, gross - commission)); // CR income: net
                if (commission > 0) {
                    lines.add(new Line(commissionPayable, 0, commission)); // CR commission payable
                }
                yield new JournalEntry(remark, cur, lines);
            }
            case PAYMENT_CAPTURED -> new JournalEntry(remark, cur, List.of(
                    new Line(cash, gross, 0),
                    new Line(premiumReceivable, 0, gross)));
            default -> throw new IllegalArgumentException("unknown ledger event type: " + row.getEventType());
        };
    }
}
