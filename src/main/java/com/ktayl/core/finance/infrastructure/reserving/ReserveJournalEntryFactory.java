package com.ktayl.core.finance.infrastructure.reserving;

import com.ktayl.core.finance.infrastructure.reserving.ReserveJournalEntry.Line;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the balanced double-entry for an IBNR reserve-movement outbox row (FIN-03, ADR-022). The account
 * mapping is the only domain knowledge here; the balance invariant lives in {@link ReserveJournalEntry}.
 * <ul>
 *   <li><b>Strengthening</b> (movement &gt; 0): DR <i>Change in IBNR</i> (P&amp;L expense) / CR
 *       <i>IBNR Provision</i> (balance-sheet liability) — the reserve is raised against the P&amp;L.</li>
 *   <li><b>Release</b> (movement &lt; 0): the reverse, |movement| — the provision is released back to the P&amp;L.</li>
 * </ul>
 * Account names come from config so they map to the actual ERPNext (French PCG) chart without a code change.
 */
@Component
public class ReserveJournalEntryFactory {

    public static final String RESERVE_MOVEMENT = "IBNR_MOVEMENT";

    private final String changeInIbnr;
    private final String ibnrProvision;

    public ReserveJournalEntryFactory(
            @Value("${reserving.ledger.accounts.change-in-ibnr:Change in IBNR}") String changeInIbnr,
            @Value("${reserving.ledger.accounts.ibnr-provision:IBNR Provision}") String ibnrProvision) {
        this.changeInIbnr = changeInIbnr;
        this.ibnrProvision = ibnrProvision;
    }

    public ReserveJournalEntry from(ReserveMovementOutboxEntity row) {
        long amt = row.getAmountMinor();
        long mag = Math.abs(amt);
        String cur = row.getCurrency();
        String remark = "IBNR movement " + row.getReference() + " (" + row.getPortfolio() + ")";
        return amt >= 0
                ? new ReserveJournalEntry(remark, cur, List.of(          // strengthening
                        new Line(changeInIbnr, mag, 0),
                        new Line(ibnrProvision, 0, mag)))
                : new ReserveJournalEntry(remark, cur, List.of(          // release
                        new Line(ibnrProvision, mag, 0),
                        new Line(changeInIbnr, 0, mag)));
    }
}
