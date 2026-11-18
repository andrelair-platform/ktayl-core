package com.ktayl.core.finance.application;

import com.ktayl.core.billing.ledgerview.LedgerReconciliationSummary;
import com.ktayl.core.billing.ledgerview.LedgerReconciliationView;
import com.ktayl.core.finance.api.FinanceCloseOperations;
import com.ktayl.core.finance.api.PeriodReconciliation;
import com.ktayl.core.finance.api.PremiumRegister;
import com.ktayl.core.finance.domain.AccountingPeriodPort;
import com.ktayl.core.finance.domain.CloseArithmetic;
import com.ktayl.core.finance.domain.GeneralLedgerReadPort;
import java.time.YearMonth;
import java.util.List;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The period-close use cases (FINCLOSE-01) — wires billing's published {@link LedgerReconciliationView}, the
 * optional GL read port, and the period registry, then maps to the published api DTOs. No business state is
 * mutated (read-only close report; the close action is FINCLOSE-02).
 *
 * <p>The GL read port is optional ({@link ObjectProvider}): on dev ERPNext is unconfigured, so no adapter
 * bean exists and the GL side is reported {@code UNAVAILABLE} — exactly as billing's drainer no-ops when the
 * ledger client is absent. A GL blip at read time is caught and likewise degraded to {@code UNAVAILABLE},
 * never a 500.
 */
@Service
public class FinanceCloseService implements FinanceCloseOperations {

    private static final Logger log = LoggerFactory.getLogger(FinanceCloseService.class);
    static final String EUR = "EUR";

    private final LedgerReconciliationView ledgerView;
    private final AccountingPeriodPort periods;
    private final ObjectProvider<GeneralLedgerReadPort> gl;

    public FinanceCloseService(LedgerReconciliationView ledgerView, AccountingPeriodPort periods,
            ObjectProvider<GeneralLedgerReadPort> gl) {
        this.ledgerView = ledgerView;
        this.periods = periods;
        this.gl = gl;
    }

    @Override
    public PeriodReconciliation reconcile(YearMonth period) {
        LedgerReconciliationSummary s = ledgerView.summarize(period);

        long subledgerPosted = s.totalMinorByStatus(LedgerReconciliationView.STATUS_POSTED);
        long pendingMinor = s.totalMinorByStatus(LedgerReconciliationView.STATUS_PENDING);
        long pendingCount = s.countByStatus(LedgerReconciliationView.STATUS_PENDING);
        long failedMinor = s.totalMinorByStatus(LedgerReconciliationView.STATUS_FAILED);
        long failedCount = s.countByStatus(LedgerReconciliationView.STATUS_FAILED);
        List<String> unexpectedCurrencies = s.currencies().stream().filter(c -> !EUR.equals(c)).sorted().toList();

        OptionalLong glTotal = readGl(period);
        Long glPostedMinor = null;
        long driftMinor = 0;
        String glStatus;
        if (glTotal.isPresent()) {
            glPostedMinor = glTotal.getAsLong();
            driftMinor = CloseArithmetic.drift(subledgerPosted, glPostedMinor);
            glStatus = driftMinor == 0 ? "RECONCILED" : "DRIFT";
        } else {
            glStatus = "UNAVAILABLE";
        }

        return new PeriodReconciliation(period.toString(), periods.statusOf(period), subledgerPosted,
                glPostedMinor, glStatus, driftMinor, pendingCount, pendingMinor, failedCount, failedMinor,
                unexpectedCurrencies);
    }

    @Override
    public PremiumRegister register(YearMonth period) {
        LedgerReconciliationSummary s = ledgerView.summarize(period);
        long invoiced = s.totalMinor(LedgerReconciliationView.EVENT_PREMIUM_INVOICED);
        long received = s.totalMinor(LedgerReconciliationView.EVENT_PAYMENT_CAPTURED);
        return new PremiumRegister(period.toString(), invoiced, received,
                CloseArithmetic.outstanding(invoiced, received), EUR);
    }

    /** Read the GL posted total, degrading any unavailability (no adapter / GL blip) to empty. */
    private OptionalLong readGl(YearMonth period) {
        GeneralLedgerReadPort port = gl.getIfAvailable();
        if (port == null) {
            return OptionalLong.empty(); // ERPNext not configured (dev) — GL side unavailable
        }
        try {
            return port.postedTotalMinor(period);
        } catch (RuntimeException ex) {
            log.warn("GL read failed for {} — reporting UNAVAILABLE: {}", period, ex.getMessage());
            return OptionalLong.empty();
        }
    }
}
