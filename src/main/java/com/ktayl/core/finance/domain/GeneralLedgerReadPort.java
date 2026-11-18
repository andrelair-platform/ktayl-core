package com.ktayl.core.finance.domain;

import java.time.YearMonth;
import java.util.OptionalLong;

/**
 * Outbound port to the general ledger for READING a period's posted total (FINCLOSE-01). Kept behind an
 * interface so the finance domain depends on "the GL's posted total for a period", not on ERPNext/Frappe —
 * the HTTP adapter lives only in {@code infrastructure.gl} and is active solely when ERPNext is configured.
 *
 * <p>Returns {@link OptionalLong#empty()} when the GL is unreachable or unconfigured — the reconciliation then
 * reports {@code UNAVAILABLE} rather than failing (the subledger view is always available regardless).
 */
public interface GeneralLedgerReadPort {

    /** The GL's total posted amount (minor units) for {@code period}, or empty if the GL can't be read. */
    OptionalLong postedTotalMinor(YearMonth period);
}
