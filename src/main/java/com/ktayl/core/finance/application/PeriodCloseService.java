package com.ktayl.core.finance.application;

import com.ktayl.core.finance.api.FinanceCloseOperations;
import com.ktayl.core.finance.api.PeriodCloseOperations;
import com.ktayl.core.finance.api.PeriodCloseRecord;
import com.ktayl.core.finance.api.PeriodReconciliation;
import com.ktayl.core.finance.domain.AccountingPeriodPort;
import com.ktayl.core.finance.domain.AccountingPeriods;
import com.ktayl.core.finance.domain.ClosingAuditEntry;
import com.ktayl.core.finance.domain.ClosingAuditPort;
import com.ktayl.core.finance.domain.IllegalPeriodTransitionException;
import com.ktayl.core.finance.domain.InvalidCloseRequestException;
import com.ktayl.core.finance.domain.PeriodCloseDecision;
import com.ktayl.core.finance.domain.PeriodLifecyclePort;
import com.ktayl.core.finance.domain.PeriodNotClosableException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The period close/reopen use cases (FINCLOSE-02). A close reuses {@link FinanceCloseOperations#reconcile} for
 * the preconditions + the attested snapshot, mutates the registry, and appends an immutable audit entry — all
 * in one transaction. The close is a control/attestation, not a physical lock on billing (ADR-019): finance
 * cannot (and must not) block billing's posting, so it attests the settled state at close and the
 * reconciliation detects any later movement.
 */
@Service
public class PeriodCloseService implements PeriodCloseOperations {

    private final FinanceCloseOperations reconcile;
    private final AccountingPeriodPort periods;
    private final PeriodLifecyclePort lifecycle;
    private final ClosingAuditPort audit;

    public PeriodCloseService(FinanceCloseOperations reconcile, AccountingPeriodPort periods,
            PeriodLifecyclePort lifecycle, ClosingAuditPort audit) {
        this.reconcile = reconcile;
        this.periods = periods;
        this.lifecycle = lifecycle;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void close(YearMonth period, boolean force, String reason, String actor) {
        if (force && isBlank(reason)) {
            throw new InvalidCloseRequestException("a forced close requires a reason");
        }
        if ("CLOSED".equals(periods.statusOf(period))) {
            throw new IllegalPeriodTransitionException("period " + period + " is already closed");
        }
        PeriodReconciliation r = reconcile.reconcile(period);
        boolean ended = AccountingPeriods.hasEnded(period, Instant.now());
        long stuck = r.pendingCount() + r.failedCount();
        PeriodCloseDecision.blocker(ended, stuck, r.glStatus(), r.driftMinor(), force)
                .ifPresent(b -> {
                    throw new PeriodNotClosableException(period, b);
                });

        lifecycle.close(period);
        audit.append(new ClosingAuditEntry(period, ClosingAuditEntry.Action.CLOSED, actor, Instant.now(),
                r.subledgerPostedMinor(), r.glPostedMinor(), r.driftMinor(), r.glStatus(), force,
                isBlank(reason) ? null : reason));
    }

    @Override
    @Transactional
    public void reopen(YearMonth period, String reason, String actor) {
        if (isBlank(reason)) {
            throw new InvalidCloseRequestException("a reopen requires a reason");
        }
        if (!"CLOSED".equals(periods.statusOf(period))) {
            throw new IllegalPeriodTransitionException("period " + period + " is not closed");
        }
        lifecycle.reopen(period);
        audit.append(new ClosingAuditEntry(period, ClosingAuditEntry.Action.REOPENED, actor, Instant.now(),
                null, null, null, null, false, reason));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PeriodCloseRecord> closeHistory(YearMonth period) {
        return audit.history(period).stream().map(PeriodCloseService::toRecord).toList();
    }

    private static PeriodCloseRecord toRecord(ClosingAuditEntry e) {
        return new PeriodCloseRecord(e.period().toString(), e.action().name(), e.actor(), e.at(),
                e.subledgerPostedMinor(), e.glPostedMinor(), e.driftMinor(), e.glStatus(), e.forced(), e.reason());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
