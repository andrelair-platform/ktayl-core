package com.ktayl.core.claimsgl.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * ERPNext (Frappe) adapter for {@link ClaimLedgerClient} (ADR-021). Posts a balanced Journal Entry via the
 * Frappe REST API, idempotently. Active ONLY when {@code ledger.erpnext.api-key} is set — the same
 * deterministic property gate as billing's ERPNext client / the NATS consumers, so dev (blank) runs with the
 * outbox accumulating and no ERPNext call. {@code @ConditionalOnExpression}, never {@code @ConditionalOnBean}
 * (the order-dependent condition that silently dropped the bound-risk consumer in prod).
 *
 * <p>Idempotency: the outbox id is written to the JE {@code cheque_no}; before posting, we query for an
 * existing JE with that {@code cheque_no} and return it if found — so a retry never duplicates the entry.
 * None of the claims legs needs a Party (no receivable), so no party wiring (unlike billing's receivable).
 *
 * <p>Near-copy of billing's {@code ErpNextLedgerClient} (WET-first, extract on the 3rd GL consumer — ADR-021).
 */
@Component
@ConditionalOnExpression("'${ledger.erpnext.api-key:}' != ''")
public class ErpNextClaimsLedgerClient implements ClaimLedgerClient {

    private static final Logger log = LoggerFactory.getLogger(ErpNextClaimsLedgerClient.class);
    private static final String JE = "/api/resource/Journal Entry";

    private final RestClient http;
    private final String company;

    public ErpNextClaimsLedgerClient(
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
    public String postJournalEntry(JournalEntry entry, String idempotencyRef) {
        String existing = findExisting(idempotencyRef);
        if (existing != null) {
            log.debug("claims GL JE for ref {} already exists as {} — idempotent", idempotencyRef, existing);
            return existing;
        }
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (JournalEntry.Line l : entry.lines()) {
            Map<String, Object> line = new HashMap<>();
            line.put("account", l.account());
            line.put("debit_in_account_currency", minorToUnits(l.debitMinor()));
            line.put("credit_in_account_currency", minorToUnits(l.creditMinor()));
            accounts.add(line);
        }
        String today = LocalDate.now().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("posting_date", today);
        body.put("company", company);
        body.put("voucher_type", "Journal Entry");
        body.put("cheque_no", idempotencyRef);
        body.put("cheque_date", today); // required by ERPNext whenever cheque_no is set (our idempotency key)
        body.put("user_remark", entry.remark());
        body.put("docstatus", 1); // submit straight to the ledger (not a draft)
        body.put("accounts", accounts);

        Map<?, ?> resp = http.post().uri(uri -> uri.path(JE).build()).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(Map.class);
        String name = dataName(resp);
        if (name == null) {
            throw new IllegalStateException("ERPNext did not return a Journal Entry name");
        }
        return name;
    }

    /** @return the existing JE name for this idempotency ref, or null. */
    private String findExisting(String ref) {
        String filters = "[[\"cheque_no\",\"=\",\"" + ref + "\"]]";
        Map<?, ?> resp = http.get().uri(uri -> uri.path(JE)
                        .queryParam("filters", filters).queryParam("limit_page_length", 1).build())
                .retrieve().body(Map.class);
        if (resp != null && resp.get("data") instanceof List<?> data && !data.isEmpty()
                && data.get(0) instanceof Map<?, ?> first) {
            Object name = first.get("name");
            return name != null ? name.toString() : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String dataName(Map<?, ?> resp) {
        if (resp != null && resp.get("data") instanceof Map<?, ?> data) {
            Object name = ((Map<String, Object>) data).get("name");
            return name != null ? name.toString() : null;
        }
        return null;
    }

    /** eurocents → account-currency units, exact (e.g. 480000 → 4800.00). Never a float. */
    private static BigDecimal minorToUnits(long minor) {
        return new BigDecimal(minor).movePointLeft(2);
    }
}
