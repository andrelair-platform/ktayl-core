package com.ktayl.core.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktayl.core.finance.api.FinanceCloseOperations;
import com.ktayl.core.finance.api.PeriodReconciliation;
import com.ktayl.core.finance.domain.AccountingPeriodPort;
import com.ktayl.core.finance.domain.ClosingAuditEntry;
import com.ktayl.core.finance.domain.ClosingAuditPort;
import com.ktayl.core.finance.domain.IllegalPeriodTransitionException;
import com.ktayl.core.finance.domain.InvalidCloseRequestException;
import com.ktayl.core.finance.domain.PeriodLifecyclePort;
import com.ktayl.core.finance.domain.PeriodNotClosableException;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * L1 — the close/reopen use cases (FINCLOSE-02): preconditions, the audit snapshot, and the state guards. The
 * ports are mocked; no Spring, no DB (the real-Postgres path is the L2 integration test). The period 2026-10
 * is in the past, so "ended" holds without mocking a clock.
 */
class PeriodCloseServiceTest {

    private static final YearMonth OCT = YearMonth.of(2026, 10);

    private final FinanceCloseOperations reconcile = mock(FinanceCloseOperations.class);
    private final AccountingPeriodPort periods = mock(AccountingPeriodPort.class);
    private final PeriodLifecyclePort lifecycle = mock(PeriodLifecyclePort.class);
    private final ClosingAuditPort audit = mock(ClosingAuditPort.class);
    private final PeriodCloseService service = new PeriodCloseService(reconcile, periods, lifecycle, audit);

    @Test
    void close_succeeds_when_ended_settled_and_reconciled() {
        when(periods.statusOf(OCT)).thenReturn("OPEN");
        when(reconcile.reconcile(OCT)).thenReturn(recon(0, 0, "RECONCILED", 0));

        service.close(OCT, false, null, "officer.sophie");

        verify(lifecycle).close(OCT);
        ArgumentCaptor<ClosingAuditEntry> captor = ArgumentCaptor.forClass(ClosingAuditEntry.class);
        verify(audit).append(captor.capture());
        ClosingAuditEntry e = captor.getValue();
        assertThat(e.action()).isEqualTo(ClosingAuditEntry.Action.CLOSED);
        assertThat(e.actor()).isEqualTo("officer.sophie");
        assertThat(e.subledgerPostedMinor()).isEqualTo(480_000L);
        assertThat(e.forced()).isFalse();
    }

    @Test
    void close_is_refused_when_subledger_not_settled() {
        when(periods.statusOf(OCT)).thenReturn("OPEN");
        when(reconcile.reconcile(OCT)).thenReturn(recon(1, 0, "RECONCILED", 0)); // one pending

        assertThatThrownBy(() -> service.close(OCT, false, null, "officer"))
                .isInstanceOf(PeriodNotClosableException.class);
        verify(lifecycle, never()).close(any());
        verify(audit, never()).append(any());
    }

    @Test
    void close_is_refused_on_gl_drift_without_force_but_allowed_with_force_and_reason() {
        when(periods.statusOf(OCT)).thenReturn("OPEN");
        when(reconcile.reconcile(OCT)).thenReturn(recon(0, 0, "DRIFT", 80_000));

        assertThatThrownBy(() -> service.close(OCT, false, null, "officer"))
                .isInstanceOf(PeriodNotClosableException.class);

        service.close(OCT, true, "manual GL adjustment pending", "officer"); // forced + reason → allowed
        verify(lifecycle).close(OCT);
    }

    @Test
    void forced_close_without_a_reason_is_422() {
        when(periods.statusOf(OCT)).thenReturn("OPEN");
        assertThatThrownBy(() -> service.close(OCT, true, "  ", "officer"))
                .isInstanceOf(InvalidCloseRequestException.class);
        verify(reconcile, never()).reconcile(any());
    }

    @Test
    void closing_an_already_closed_period_is_a_conflict() {
        when(periods.statusOf(OCT)).thenReturn("CLOSED");
        assertThatThrownBy(() -> service.close(OCT, false, null, "officer"))
                .isInstanceOf(IllegalPeriodTransitionException.class);
    }

    @Test
    void reopen_requires_a_reason_and_a_closed_period() {
        when(periods.statusOf(OCT)).thenReturn("CLOSED");
        assertThatThrownBy(() -> service.reopen(OCT, " ", "officer"))
                .isInstanceOf(InvalidCloseRequestException.class);

        when(periods.statusOf(OCT)).thenReturn("OPEN");
        assertThatThrownBy(() -> service.reopen(OCT, "correction", "officer"))
                .isInstanceOf(IllegalPeriodTransitionException.class);
    }

    @Test
    void reopen_succeeds_on_a_closed_period() {
        when(periods.statusOf(OCT)).thenReturn("CLOSED");

        service.reopen(OCT, "post-close correction", "officer.sophie");

        verify(lifecycle).reopen(OCT);
        ArgumentCaptor<ClosingAuditEntry> captor = ArgumentCaptor.forClass(ClosingAuditEntry.class);
        verify(audit).append(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(ClosingAuditEntry.Action.REOPENED);
        assertThat(captor.getValue().reason()).isEqualTo("post-close correction");
    }

    private static PeriodReconciliation recon(long pendingCount, long failedCount, String glStatus, long drift) {
        Long glPosted = "UNAVAILABLE".equals(glStatus) ? null : 480_000L - drift;
        return new PeriodReconciliation("2026-10", "OPEN", 480_000L, glPosted, glStatus, drift,
                pendingCount, 0L, failedCount, 0L, List.of(), null, false);
    }
}
