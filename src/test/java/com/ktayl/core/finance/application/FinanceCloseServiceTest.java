package com.ktayl.core.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktayl.core.billing.ledgerview.LedgerReconciliationSummary;
import com.ktayl.core.billing.ledgerview.LedgerReconciliationSummary.Bucket;
import com.ktayl.core.billing.ledgerview.LedgerReconciliationView;
import com.ktayl.core.finance.api.PeriodReconciliation;
import com.ktayl.core.finance.api.PremiumRegister;
import com.ktayl.core.finance.domain.AccountingPeriodPort;
import com.ktayl.core.finance.domain.GeneralLedgerReadPort;
import java.time.YearMonth;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * L1 — the finance close service maps billing's reconciliation summary + the (optional) GL read into the
 * published DTOs (FINCLOSE-01 AC-2/4/5/7). Pure unit: the billing view, the period port and the GL provider
 * are stubbed; no Spring, no DB.
 */
class FinanceCloseServiceTest {

    private static final YearMonth OCT = YearMonth.of(2026, 10);

    private final LedgerReconciliationView view = mock(LedgerReconciliationView.class);
    private final AccountingPeriodPort periods = mock(AccountingPeriodPort.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<GeneralLedgerReadPort> glProvider = mock(ObjectProvider.class);
    private final FinanceCloseService service = new FinanceCloseService(view, periods, glProvider);

    @BeforeEach
    void stubSummary() {
        // POSTED: 480000 (premium) + 300000 (cash) = 780000 subledger-posted
        // PENDING: 120000 premium (1 row);  FAILED: 120000 cash (1 row, stuck)
        LedgerReconciliationSummary summary = new LedgerReconciliationSummary(OCT, List.of(
                new Bucket("PREMIUM_INVOICED", "POSTED", 2, 480_000),
                new Bucket("PREMIUM_INVOICED", "PENDING", 1, 120_000),
                new Bucket("PAYMENT_CAPTURED", "POSTED", 1, 300_000),
                new Bucket("PAYMENT_CAPTURED", "FAILED", 1, 120_000)),
                Set.of("EUR"));
        when(view.summarize(OCT)).thenReturn(summary);
        when(periods.statusOf(OCT)).thenReturn("OPEN");
    }

    @Test
    void reconcile_reports_unavailable_when_no_gl_adapter() {
        when(glProvider.getIfAvailable()).thenReturn(null);

        PeriodReconciliation r = service.reconcile(OCT);

        assertThat(r.period()).isEqualTo("2026-10");
        assertThat(r.periodStatus()).isEqualTo("OPEN");
        assertThat(r.subledgerPostedMinor()).isEqualTo(780_000L);
        assertThat(r.glPostedMinor()).isNull();
        assertThat(r.glStatus()).isEqualTo("UNAVAILABLE");
        assertThat(r.driftMinor()).isZero();
        assertThat(r.pendingCount()).isEqualTo(1L);
        assertThat(r.pendingMinor()).isEqualTo(120_000L);
        assertThat(r.failedCount()).isEqualTo(1L);
        assertThat(r.failedMinor()).isEqualTo(120_000L);
        assertThat(r.unexpectedCurrencies()).isEmpty();
    }

    @Test
    void reconcile_is_reconciled_when_gl_matches_subledger() {
        GeneralLedgerReadPort gl = mock(GeneralLedgerReadPort.class);
        when(glProvider.getIfAvailable()).thenReturn(gl);
        when(gl.postedTotalMinor(OCT)).thenReturn(OptionalLong.of(780_000L));

        PeriodReconciliation r = service.reconcile(OCT);

        assertThat(r.glPostedMinor()).isEqualTo(780_000L);
        assertThat(r.glStatus()).isEqualTo("RECONCILED");
        assertThat(r.driftMinor()).isZero();
    }

    @Test
    void reconcile_reports_drift_when_gl_differs() {
        GeneralLedgerReadPort gl = mock(GeneralLedgerReadPort.class);
        when(glProvider.getIfAvailable()).thenReturn(gl);
        when(gl.postedTotalMinor(OCT)).thenReturn(OptionalLong.of(700_000L));

        PeriodReconciliation r = service.reconcile(OCT);

        assertThat(r.glStatus()).isEqualTo("DRIFT");
        assertThat(r.driftMinor()).isEqualTo(80_000L);
    }

    @Test
    void reconcile_degrades_to_unavailable_when_gl_throws() {
        GeneralLedgerReadPort gl = mock(GeneralLedgerReadPort.class);
        when(glProvider.getIfAvailable()).thenReturn(gl);
        when(gl.postedTotalMinor(any())).thenThrow(new RuntimeException("ERPNext 503"));

        PeriodReconciliation r = service.reconcile(OCT);

        assertThat(r.glStatus()).isEqualTo("UNAVAILABLE");
        assertThat(r.glPostedMinor()).isNull();
        assertThat(r.driftMinor()).isZero();
    }

    @Test
    void register_ties_out_invoiced_received_outstanding() {
        PremiumRegister reg = service.register(OCT);

        assertThat(reg.grossPremiumInvoicedMinor()).isEqualTo(600_000L); // 480000 + 120000
        assertThat(reg.cashReceivedMinor()).isEqualTo(420_000L);         // 300000 + 120000
        assertThat(reg.receivableOutstandingMinor()).isEqualTo(180_000L);
        assertThat(reg.currency()).isEqualTo("EUR");
    }

    @Test
    void reconcile_surfaces_non_eur_currencies_as_exceptions() {
        when(view.summarize(OCT)).thenReturn(new LedgerReconciliationSummary(OCT, List.of(
                new Bucket("PREMIUM_INVOICED", "POSTED", 1, 100_000)), Set.of("EUR", "USD", "GBP")));
        when(glProvider.getIfAvailable()).thenReturn(null);

        PeriodReconciliation r = service.reconcile(OCT);

        assertThat(r.unexpectedCurrencies()).containsExactly("GBP", "USD");
    }
}
