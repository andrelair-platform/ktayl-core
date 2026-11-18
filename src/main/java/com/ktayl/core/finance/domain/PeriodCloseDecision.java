package com.ktayl.core.finance.domain;

import java.util.Optional;

/**
 * The pure decision of whether a period may be closed (FINCLOSE-02 AC-1). A close is refused unless: the
 * period has ended, the subledger is settled (no stuck postings), and the GL agrees (no drift) — drift may be
 * overridden with an explicit {@code force}. Framework-free, so it is unit-tested in isolation.
 */
public final class PeriodCloseDecision {

    private PeriodCloseDecision() {}

    /** Why a close was refused (each maps to a 409 with this reason). */
    public enum Blocker {
        PERIOD_NOT_ENDED,
        SUBLEDGER_NOT_SETTLED,
        GL_DRIFT_NOT_FORCED
    }

    /**
     * @param stuckCount pending + failed postings in the period (settled ⇔ 0)
     * @return the blocker, or empty if the close is allowed
     */
    public static Optional<Blocker> blocker(boolean periodEnded, long stuckCount, String glStatus,
            long driftMinor, boolean force) {
        if (!periodEnded) {
            return Optional.of(Blocker.PERIOD_NOT_ENDED);
        }
        if (stuckCount > 0) {
            return Optional.of(Blocker.SUBLEDGER_NOT_SETTLED);
        }
        if ("DRIFT".equals(glStatus) && driftMinor != 0 && !force) {
            return Optional.of(Blocker.GL_DRIFT_NOT_FORCED);
        }
        return Optional.empty();
    }
}
