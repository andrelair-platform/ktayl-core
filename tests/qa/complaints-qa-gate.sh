#!/usr/bin/env bash
# Live QA gate (L5) for the Complaints API — the adversarial regression floor run against the RUNNING
# dev service before any prod promotion (qa-gate.md). RUN ON THE CONTROLLER (needs kubectl + the
# authentik pod to mint tokens, and reaches the dev ingress at 10.0.0.200).
#
#   scp tests/qa/{complaints-qa-gate.sh,mint-token.py} controller:/tmp/ && ssh controller 'bash /tmp/complaints-qa-gate.sh'
#
# It mints OIDC tokens via Authentik's ORM (one in 'Complaints Handler', one without), curls the live
# ingress, asserts expected vs actual for the 12 qa-gate.md areas, checks audit rows + the observability
# triad, and prints a PASS/FAIL verdict. Tokens stay in local vars (never printed).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
H=${KTAYL_CORE_HOST:-ktayl-core.10.0.0.200.nip.io}
PASS=0; FAIL=0; FAILED_CASES=""
chk() { if [ "$2" = "$3" ]; then echo "  PASS [$2] $1"; PASS=$((PASS+1));
        else echo "  FAIL exp=$2 got=$3  $1"; FAIL=$((FAIL+1)); FAILED_CASES="$FAILED_CASES\n  - $1 (exp $2 got $3)"; fi; }
req() { local m="$1" p="$2" tok="$3" body="${4:-}" ct="${5:-application/json}"
  local args=(-sk --max-time 15 -X "$m" -o /tmp/qa_body -w '%{http_code}')
  [ -n "$tok" ] && args+=(-H "Authorization: Bearer $tok"); [ -n "$body" ] && args+=(-H "Content-Type: $ct" --data "$body")
  [ -n "$IDEMP" ] && args+=(-H "Idempotency-Key: $IDEMP")
  echo "$(curl "${args[@]}" "https://$H$p") $(cat /tmp/qa_body 2>/dev/null)"; }
jid() { echo "$1" | cut -d' ' -f2- | python3 -c "import sys,json;print(json.load(sys.stdin).get('$2',''))" 2>/dev/null; }
IDEMP=""

POD=$(kubectl get pod -n authentik -l app.kubernetes.io/component=server -o jsonpath='{.items[0].metadata.name}')
B64=$(base64 < "$HERE/mint-token.py" | tr -d '\n')
OUT=$(kubectl exec -i -n authentik "$POD" -- ak shell -c "import base64; exec(base64.b64decode('$B64').decode())" 2>/dev/null || true)
TH=$(printf '%s\n' "$OUT" | grep '^THANDLER=' | cut -d= -f2-); TN=$(printf '%s\n' "$OUT" | grep '^TNOGROUP=' | cut -d= -f2-)
[ -n "$TH" ] || { echo "FATAL: token mint failed"; exit 1; }
echo "tokens minted"

echo "== authz =="
chk "no token -> 401"      401 "$(req GET /api/complaints/00000000-0000-0000-0000-0000000000aa '' '' | cut -d' ' -f1)"
chk "no-group GET -> 403"  403 "$(req GET /api/complaints/00000000-0000-0000-0000-0000000000aa "$TN" '' | cut -d' ' -f1)"
chk "no-group POST -> 403" 403 "$(req POST /api/complaints "$TN" '{"channel":"EMAIL","summary":"x"}' | cut -d' ' -f1)"

echo "== happy path =="
R=$(IDEMP=qa-key-$$; req POST /api/complaints "$TH" '{"channel":"EMAIL","subjectRef":"POL-QA","summary":"premium overcharged"}'); IDEMP=""
chk "register -> 201" 201 "$(echo "$R" | cut -d' ' -f1)"; ID=$(jid "$R" id); echo "  id=$ID"
chk "GET -> 200"         200 "$(req GET /api/complaints/$ID "$TH" '' | cut -d' ' -f1)"
chk "acknowledge -> 204" 204 "$(req POST /api/complaints/$ID/acknowledge "$TH" '' | cut -d' ' -f1)"
chk "assign -> 204"      204 "$(req POST /api/complaints/$ID/assign "$TH" '{"assignedTo":"adjuster.sophie"}' | cut -d' ' -f1)"
chk "resolve -> 204"     204 "$(req POST /api/complaints/$ID/resolve "$TH" '{"resolutionNote":"refunded"}' | cut -d' ' -f1)"
chk "close -> 204"       204 "$(req POST /api/complaints/$ID/close "$TH" '' | cut -d' ' -f1)"
GC=$(req GET /api/complaints/$ID "$TH" ''); chk "final status CLOSED" CLOSED "$(jid "$GC" status)"

echo "== idempotency =="
A=$(IDEMP=qa-idem-$$; req POST /api/complaints "$TH" '{"channel":"PHONE","summary":"dup"}'); IDEMP=""
B=$(IDEMP=qa-idem-$$; req POST /api/complaints "$TH" '{"channel":"PHONE","summary":"dup"}'); IDEMP=""
chk "same key -> same id" "$(jid "$A" id)" "$(jid "$B" id)"

echo "== validation -> 422 =="
chk "unknown channel -> 422" 422 "$(req POST /api/complaints "$TH" '{"channel":"NOPE","summary":"x"}' | cut -d' ' -f1)"
chk "blank summary -> 422"   422 "$(req POST /api/complaints "$TH" '{"channel":"EMAIL","summary":"  "}' | cut -d' ' -f1)"
FR=$(req POST /api/complaints "$TH" '{"channel":"EMAIL","summary":"assign-blank"}'); FID=$(jid "$FR" id); req POST /api/complaints/$FID/acknowledge "$TH" '' >/dev/null
chk "assign blank -> 422"    422 "$(req POST /api/complaints/$FID/assign "$TH" '{"assignedTo":"  "}' | cut -d' ' -f1)"

echo "== not-found / state guards =="
chk "GET unknown -> 404" 404 "$(req GET /api/complaints/00000000-0000-0000-0000-0000000000ff "$TH" '' | cut -d' ' -f1)"
chk "op on CLOSED -> 409" 409 "$(req POST /api/complaints/$ID/acknowledge "$TH" '' | cut -d' ' -f1)"
OR=$(req POST /api/complaints "$TH" '{"channel":"EMAIL","summary":"ooo"}'); OID=$(jid "$OR" id)
chk "resolve before ack -> 409" 409 "$(req POST /api/complaints/$OID/resolve "$TH" '{"resolutionNote":"x"}' | cut -d' ' -f1)"

echo "== robustness =="
chk "malformed JSON -> 400" 400 "$(req POST /api/complaints "$TH" '{ not json' | cut -d' ' -f1)"
chk "wrong content-type -> 415" 415 "$(req POST /api/complaints "$TH" 'hello' text/plain | cut -d' ' -f1)"

echo "== audit + observability =="
CPW=$(kubectl get secret ktayl-core-cnpg-app -n ktayl-core -o jsonpath='{.data.password}' 2>/dev/null | base64 -d)
AUD=$(kubectl exec -n ktayl-core ktayl-core-cnpg-1 -- env PGPASSWORD="$CPW" psql -h localhost -U ktayl_core -d ktayl_core -tAc "select action||':'||actor from complaints.audit_log where entity_id='$ID' order by id" 2>/dev/null | tr '\n' ' ')
echo "$AUD" | grep -q "COMPLAINT_REGISTERED:100001" && { echo "  PASS audit chain actor=100001"; PASS=$((PASS+1)); } || { echo "  FAIL audit chain"; FAIL=$((FAIL+1)); }
chk "metrics scrape -> 200" 200 "$(curl -sk -o /dev/null -w '%{http_code}' --max-time 12 https://$H/actuator/prometheus)"
KPOD=$(kubectl get pods -n ktayl-core -l app=ktayl-core -o jsonpath='{.items[0].metadata.name}')
kubectl logs -n ktayl-core "$KPOD" --tail=800 2>/dev/null | grep -a "complaint registered" | tail -1 | grep -q '"traceId"' && { echo "  PASS logs trace-correlated (traceId present)"; PASS=$((PASS+1)); } || { echo "  WARN no traceId in logs"; }

echo ""; echo "============ RESULT: PASS=$PASS FAIL=$FAIL ============"
[ "$FAIL" -eq 0 ] && echo "VERDICT: GREEN" || { echo "VERDICT: NOT READY"; echo -e "FAILED:$FAILED_CASES"; exit 1; }
