package com.ktayl.core.finance.domain;

import java.time.YearMonth;

/**
 * A period cannot be closed because a precondition failed (not ended / subledger not settled / GL drift not
 * forced). Mapped to HTTP 409 — the request conflicts with the period's current reconciliation state.
 */
public class PeriodNotClosableException extends RuntimeException {

    private final PeriodCloseDecision.Blocker blocker;

    public PeriodNotClosableException(YearMonth period, PeriodCloseDecision.Blocker blocker) {
        super("period " + period + " cannot be closed: " + reason(blocker));
        this.blocker = blocker;
    }

    public PeriodCloseDecision.Blocker blocker() {
        return blocker;
    }

    private static String reason(PeriodCloseDecision.Blocker blocker) {
        return switch (blocker) {
            case PERIOD_NOT_ENDED -> "the period has not ended yet";
            case SUBLEDGER_NOT_SETTLED -> "the subledger is not settled (pending/failed GL postings remain)";
            case GL_DRIFT_NOT_FORCED -> "the GL does not reconcile (drift ≠ 0) — resubmit with force + a reason";
        };
    }
}
