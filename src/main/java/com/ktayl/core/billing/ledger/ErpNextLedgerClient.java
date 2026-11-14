package com.ktayl.core.billing.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * ERPNext (Frappe) adapter for {@link LedgerClient} (ADR-002). Posts a balanced Journal Entry via the Frappe
 * REST API, idempotently. Active ONLY when {@code ledger.erpnext.api-key} is set — the same deterministic
 * property gate as the NATS consumer / Stripe gateway, so dev (blank) runs with the outbox accumulating and
 * no ERPNext call. {@code @ConditionalOnExpression}, never {@code @ConditionalOnBean} (the order-dependent
 * condition that silently dropped the bound-risk consumer in prod).
 *
 * <p><b>Idempotency:</b> the outbox id is written to the JE {@code cheque_no}; before posting, we query for
 * an existing JE with that {@code cheque_no} and return it if found — so a retry (or a crash between the
 * post and the DB mark-posted) never creates a duplicate ledger entry.
 */
@Component
@ConditionalOnExpression("'${ledger.erpnext.api-key:}' != ''")
public class ErpNextLedgerClient implements LedgerClient {

    private static final Logger log = LoggerFactory.getLogger(ErpNextLedgerClient.class);
    private static final String JE = "/api/resource/Journal Entry";

    private final RestClient http;
    private final String company;

    public ErpNextLedgerClient(
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
            log.debug("GL JE for ref {} already exists as {} — idempotent", idempotencyRef, existing);
            return existing;
        }
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (JournalEntry.Line l : entry.lines()) {
            accounts.add(Map.of(
                    "account", l.account(),
                    "debit_in_account_currency", minorToUnits(l.debitMinor()),
                    "credit_in_account_currency", minorToUnits(l.creditMinor())));
        }
        Map<String, Object> body = Map.of(
                "posting_date", LocalDate.now().toString(),
                "company", company,
                "voucher_type", "Journal Entry",
                "cheque_no", idempotencyRef,
                "user_remark", entry.remark(),
                "docstatus", 1, // submit straight to the ledger (not a draft)
                "accounts", accounts);

        Map<?, ?> resp = http.post().uri(JE).contentType(MediaType.APPLICATION_JSON).body(body)
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
