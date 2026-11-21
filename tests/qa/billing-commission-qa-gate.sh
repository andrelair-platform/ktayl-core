#!/usr/bin/env bash
# Live QA gate (L5) for the BILLING commission split (BILL-012b) — the adversarial regression floor run
# against the RUNNING dev service before prod promotion (qa-gate.md). RUN ON THE CONTROLLER (needs kubectl,
# the authentik pod to mint a Finance token, and reaches the dev ingress + dev CNPG at 10.0.0.200).
#
#   scp tests/qa/{billing-commission-qa-gate.sh,mint-finance-token.py} controller:/tmp/ \
#     && ssh controller 'bash /tmp/billing-commission-qa-gate.sh'
#
# Scope note: dev's BILLING_NATS_URL is BLANK by design (prod is the single authoritative UNDERWRITING_EVENTS
# consumer — the no-double-invoice discipline), so the NATS→ingest path is OFF on dev. The split logic under
# test (InvoiceService + CommissionSchedule + the 3-leg GL mapping) is exercised here via the invoice API
# against the REAL dev schema + CHECK constraints: we seed the ingested_policy precondition directly, then
# raise the invoice as Finance and assert the persisted gross/net/commission split + the ledger_outbox row.
# The full bound-risk→NATS→ERPNext 3-leg JE is re-proven at the prod gate (where the consumer + keys are on).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
H=${KTAYL_CORE_HOST:-ktayl-core.10.0.0.200.nip.io}
NS=${KTAYL_CORE_NS:-ktayl-core}
CNPG=${KTAYL_CORE_CNPG:-ktayl-core-cnpg-1}
POLICY="QA-BILL-$$"
GROSS=480000; EXP_BPS=1500; EXP_COMMISSION=72000; EXP_NET=408000   # 15% default on 4800.00 EUR
PASS=0; FAIL=0; FAILED=""
chk() { if [ "$2" = "$3" ]; then echo "  PASS [$2] $1"; PASS=$((PASS+1));
        else echo "  FAIL exp=$2 got=$3  $1"; FAIL=$((FAIL+1)); FAILED="$FAILED\n  - $1 (exp $2 got $3)"; fi; }
code() { local m="$1" p="$2" tok="$3"; local a=(-sk --max-time 15 -X "$m" -o /tmp/bqa_body -w '%{http_code}')
  [ -n "$tok" ] && a+=(-H "Authorization: Bearer $tok"); curl "${a[@]}" "https://$H$p"; }
jget() { python3 -c "import sys,json;print(json.load(open('/tmp/bqa_body')).get('$1',''))" 2>/dev/null; }

CPW=$(kubectl get secret ktayl-core-cnpg-app -n "$NS" -o jsonpath='{.data.password}' 2>/dev/null | base64 -d)
psqlc() { kubectl exec -n "$NS" "$CNPG" -- env PGPASSWORD="$CPW" psql -h localhost -U ktayl_core -d ktayl_core -tAc "$1" 2>/dev/null | tr -d '[:space:]'; }
cleanup() {
  local iid; iid=$(psqlc "select id from billing.invoice where policy_number='$POLICY'")
  [ -n "$iid" ] && psqlc "delete from billing.installment where invoice_id='$iid'" >/dev/null
  psqlc "delete from billing.ledger_outbox where reference='$POLICY'" >/dev/null
  psqlc "delete from billing.invoice where policy_number='$POLICY'" >/dev/null
  psqlc "delete from billing.audit_log where entity_id='$iid'" >/dev/null
  psqlc "delete from billing.ingested_policy where policy_number='$POLICY'" >/dev/null
  echo "cleaned up test rows for $POLICY"
}
trap cleanup EXIT

# --- mint tokens ---
POD=$(kubectl get pod -n authentik -l app.kubernetes.io/component=server -o jsonpath='{.items[0].metadata.name}')
B64=$(base64 < "$HERE/mint-finance-token.py" | tr -d '\n')
OUT=$(kubectl exec -i -n authentik "$POD" -- ak shell -c "import base64; exec(base64.b64decode('$B64').decode())" 2>/dev/null || true)
TF=$(printf '%s\n' "$OUT" | grep '^TFINANCE=' | cut -d= -f2-); TN=$(printf '%s\n' "$OUT" | grep '^TNOGROUP=' | cut -d= -f2-)
[ -n "$TF" ] || { echo "FATAL: token mint failed"; exit 1; }
echo "tokens minted (Finance + no-group)"

echo "== authz =="
chk "no token -> 401"      401 "$(code GET "/api/billing/policies/$POLICY" '')"
chk "no-group GET -> 403"  403 "$(code GET "/api/billing/policies/$POLICY" "$TN")"
chk "no-group raise -> 403" 403 "$(code POST "/api/billing/policies/$POLICY/invoice?installments=3" "$TN")"

echo "== raise before ingest -> 404 (policy not ingested) =="
chk "raise unknown policy -> 404" 404 "$(code POST "/api/billing/policies/$POLICY/invoice?installments=3" "$TF")"

echo "== seed the ingested-policy precondition + raise the invoice (Finance) =="
psqlc "insert into billing.ingested_policy(policy_number,premium_minor,currency,product_code,effective_date,expiry_date,submission_id,quote_id,status,ingested_at) values('$POLICY',$GROSS,'EUR','PROPERTY','2026-01-01','2027-01-01','QA','QA','ingested',now())" >/dev/null
chk "ingested_policy seeded" 1 "$(psqlc "select count(*) from billing.ingested_policy where policy_number='$POLICY'")"
chk "raise -> 201" 201 "$(code POST "/api/billing/policies/$POLICY/invoice?installments=3" "$TF")"

echo "== the API view exposes the split =="
code GET "/api/billing/policies/$POLICY" "$TF" >/dev/null
chk "view grossMinor"      "$GROSS"          "$(jget grossMinor)"
chk "view netMinor"        "$EXP_NET"        "$(jget netMinor)"
chk "view commissionMinor" "$EXP_COMMISSION" "$(jget commissionMinor)"
chk "view commissionBps"   "$EXP_BPS"        "$(jget commissionBps)"
chk "view totalMinor==gross" "$GROSS"        "$(jget totalMinor)"

echo "== the persisted invoice reconciles on the REAL DB (CHECK constraints held) =="
chk "db gross_minor"      "$GROSS"          "$(psqlc "select gross_minor from billing.invoice where policy_number='$POLICY'")"
chk "db net_minor"        "$EXP_NET"        "$(psqlc "select net_minor from billing.invoice where policy_number='$POLICY'")"
chk "db commission_minor" "$EXP_COMMISSION" "$(psqlc "select commission_minor from billing.invoice where policy_number='$POLICY'")"
chk "db commission_bps"   "$EXP_BPS"        "$(psqlc "select commission_bps from billing.invoice where policy_number='$POLICY'")"
chk "db reconciles net+commission=gross" "$GROSS" "$(psqlc "select net_minor+commission_minor from billing.invoice where policy_number='$POLICY'")"

echo "== installments still bill the GROSS (schedule unchanged) =="
chk "installments sum = gross" "$GROSS" "$(psqlc "select coalesce(sum(i.amount_minor),0) from billing.installment i join billing.invoice v on v.id=i.invoice_id where v.policy_number='$POLICY'")"
chk "installment count = 3" 3 "$(psqlc "select count(*) from billing.installment i join billing.invoice v on v.id=i.invoice_id where v.policy_number='$POLICY'")"

echo "== GL outbox carries the commission for the 3-leg JE =="
chk "outbox PREMIUM_INVOICED commission_minor" "$EXP_COMMISSION" "$(psqlc "select commission_minor from billing.ledger_outbox where event_type='PREMIUM_INVOICED' and reference='$POLICY'")"
chk "outbox amount = gross" "$GROSS" "$(psqlc "select amount_minor from billing.ledger_outbox where event_type='PREMIUM_INVOICED' and reference='$POLICY'")"

echo "== idempotent raise =="
chk "re-raise -> 200 (idempotent)" 200 "$(code POST "/api/billing/policies/$POLICY/invoice?installments=3" "$TF")"
chk "still exactly 1 invoice" 1 "$(psqlc "select count(*) from billing.invoice where policy_number='$POLICY'")"

echo ""; echo "============ RESULT: PASS=$PASS FAIL=$FAIL ============"
[ "$FAIL" -eq 0 ] && echo "VERDICT: GREEN" || { echo "VERDICT: NOT READY"; echo -e "FAILED:$FAILED"; exit 1; }
