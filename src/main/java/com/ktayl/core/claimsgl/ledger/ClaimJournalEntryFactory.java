package com.ktayl.core.claimsgl.ledger;

import com.ktayl.core.claimsgl.ledger.JournalEntry.Line;
import com.ktayl.core.claimsgl.persistence.ClaimLedgerOutboxEntity;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the balanced double-entry for a claims GL outbox row (ADR-021). The account mapping is the only
 * domain knowledge here; the balance invariant is enforced by {@link JournalEntry} itself.
 * <ul>
 *   <li><b>RESERVE_MOVEMENT</b> (a reserve set/adjust) — an increase (amount &gt; 0): DR <i>Claims incurred</i>
 *       / CR <i>Outstanding claims provision</i>; a downward adjustment (amount &lt; 0): the reverse, |amount|.</li>
 *   <li><b>CLAIM_PAYMENT</b> (a loss payment, amount &gt; 0): DR <i>Outstanding claims provision</i> / CR
 *       <i>Cash</i> — the claim pays out of the provision it was reserved against.</li>
 * </ul>
 * Account names come from config so they map to the actual ERPNext chart of accounts without a code change.
 */
@Component
public class ClaimJournalEntryFactory {

    public static final String RESERVE_MOVEMENT = "RESERVE_MOVEMENT";
    public static final String CLAIM_PAYMENT = "CLAIM_PAYMENT";

    private final String claimsIncurred;
    private final String claimsProvision;
    private final String cash;

    public ClaimJournalEntryFactory(
            @Value("${claims.ledger.accounts.incurred:Claims Incurred}") String claimsIncurred,
            @Value("${claims.ledger.accounts.provision:Outstanding Claims Provision}") String claimsProvision,
            @Value("${claims.ledger.accounts.cash:Cash}") String cash) {
        this.claimsIncurred = claimsIncurred;
        this.claimsProvision = claimsProvision;
        this.cash = cash;
    }

    public JournalEntry from(ClaimLedgerOutboxEntity row) {
        long amt = row.getAmountMinor();
        String cur = row.getCurrency();
        String remark = row.getEventType() + " " + row.getReference() + " (" + row.getClaimNumber() + ")";
        return switch (row.getEventType()) {
            case RESERVE_MOVEMENT -> {
                long mag = Math.abs(amt);
                yield amt >= 0
                        ? new JournalEntry(remark, cur, List.of(          // reserve increase
                                new Line(claimsIncurred, mag, 0),
                                new Line(claimsProvision, 0, mag)))
                        : new JournalEntry(remark, cur, List.of(          // downward adjustment (release)
                                new Line(claimsProvision, mag, 0),
                                new Line(claimsIncurred, 0, mag)));
            }
            case CLAIM_PAYMENT -> new JournalEntry(remark, cur, List.of(
                    new Line(claimsProvision, amt, 0),
                    new Line(cash, 0, amt)));
            default -> throw new IllegalArgumentException("unknown claims ledger event type: " + row.getEventType());
        };
    }
}
