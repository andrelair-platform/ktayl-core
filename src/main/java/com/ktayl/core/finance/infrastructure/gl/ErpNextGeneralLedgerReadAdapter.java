package com.ktayl.core.finance.infrastructure.gl;

import com.ktayl.core.finance.domain.GeneralLedgerReadPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * ERPNext (Frappe) adapter for {@link GeneralLedgerReadPort} (FINCLOSE-01). Reads the GL's posted total for a
 * period by summing the {@code total_debit} of SUBMITTED ({@code docstatus = 1}) Journal Entries whose
 * {@code posting_date} falls in the month, for the configured company. Active ONLY when
 * {@code ledger.erpnext.api-key} is set — the SAME deterministic property gate as billing's
 * {@code ErpNextLedgerClient}, so on dev (blank key) no bean exists and the finance reconciliation reports the
 * GL side {@code UNAVAILABLE}.
 *
 * <p><b>Known assumption (v1, documented in the ADR):</b> billing is the only poster to this company's ledger
 * in the period, so the period JE total == the billing subledger total. When another poster is added, this
 * must filter to billing's JEs (e.g. by the {@code cheque_no} idempotency-ref convention). Reconciling drift
 * is exactly what would surface a violation of this assumption.
 */
@Component
@ConditionalOnExpression("'${ledger.erpnext.api-key:}' != ''")
class ErpNextGeneralLedgerReadAdapter implements GeneralLedgerReadPort {

    private static final Logger log = LoggerFactory.getLogger(ErpNextGeneralLedgerReadAdapter.class);
    private static final String JE = "/api/resource/Journal Entry";

    private final RestClient http;
    private final String company;

    ErpNextGeneralLedgerReadAdapter(
            @Value("${ledger.erpnext.base-url:http://erpnext.erp.svc:8080}") String baseUrl,
            @Value("${ledger.erpnext.api-key}") String apiKey,
            @Value("${ledger.erpnext.api-secret:}") String apiSecret,
            @Value("${ledger.erpnext.company:ktayl-solution}") String company) {
        this.company = company;
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "token " + apiKey + ":" + apiSecret)
                .build();
    }

    @Override
    public OptionalLong postedTotalMinor(YearMonth period) {
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();
        String filters = "[[\"posting_date\",\">=\",\"" + start + "\"],"
                + "[\"posting_date\",\"<=\",\"" + end + "\"],"
                + "[\"docstatus\",\"=\",1],"
                + "[\"company\",\"=\",\"" + company + "\"]]";

        Map<?, ?> resp = http.get().uri(uri -> uri.path(JE)
                        .queryParam("filters", filters)
                        .queryParam("fields", "[\"total_debit\"]")
                        .queryParam("limit_page_length", 0) // 0 = all rows (Frappe)
                        .build())
                .retrieve().body(Map.class);

        if (resp == null || !(resp.get("data") instanceof List<?> data)) {
            throw new IllegalStateException("ERPNext returned no data array for Journal Entry query");
        }
        long totalMinor = 0;
        for (Object row : data) {
            if (row instanceof Map<?, ?> je && je.get("total_debit") != null) {
                // units → eurocents, exact (e.g. 4800.00 → 480000). Never a float.
                totalMinor += new BigDecimal(je.get("total_debit").toString()).movePointRight(2).longValueExact();
            }
        }
        log.debug("GL posted total for {} = {} minor ({} JEs)", period, totalMinor, data.size());
        return OptionalLong.of(totalMinor);
    }
}
