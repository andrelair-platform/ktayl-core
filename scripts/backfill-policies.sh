#!/usr/bin/env bash
#
# backfill-policies.sh — ADR-014 Phase 2/4: copy existing Go-PAS policies (ktayl-policy-service) into the
# ktayl-core `policy` schema, once, per environment. Runs on the CONTROLLER (needs kubectl to both DB pods).
#
# WHAT IT DOES
#   1. EXPORT + TRANSFORM (in the source DB pod): reads the PAS `policies` table and maps each row to the
#      ktayl-core shape — status → the ktayl-core enum (CASE map; an UNKNOWN status → NULL → a loud
#      NOT-NULL failure on import, never a silent mismap), dates TIMESTAMPTZ → DATE. Coverages/premiums are
#      deliberately NOT carried (orphan PAS schema + premiums are billing's SoR — see the SPEC).
#   2. IMPORT (in the target CNPG primary pod): one psql session loads a TEMP table via \copy, then
#      INSERT … ON CONFLICT (policy_number) DO NOTHING  → idempotent; re-running inserts nothing.
#   3. AUDIT: one immutable BACKFILL row per newly-inserted policy (DORA Art.9) — idempotent (WHERE NOT EXISTS).
#
# IDS + TIMESTAMPS are preserved from the source, so a backfilled policy keeps its identity and the
# underwriting 409-resolve path finds it on a re-bind.
#
# USAGE
#   ./backfill-policies.sh <dry-run|apply>
#   Override the source/target via env (defaults = the DEV topology):
#     SRC_NS SRC_POD SRC_USER SRC_DB   TGT_NS TGT_DB   (TGT_POD auto-detected = the CNPG primary)
#   Phase-4 PROD example:
#     SRC_NS=ktayl SRC_POD=ktayl-cnpg-1 SRC_USER=postgres SRC_DB=ktayl_policy \
#     TGT_NS=ktayl-core-prod TGT_DB=ktayl_core ./backfill-policies.sh dry-run
#
set -euo pipefail
MODE="${1:?usage: backfill-policies.sh <dry-run|apply>}"

SRC_NS="${SRC_NS:-ktayl}"; SRC_POD="${SRC_POD:-ktayl-postgres-0}"
SRC_USER="${SRC_USER:-ktayl}"; SRC_DB="${SRC_DB:-ktayl_policy}"
TGT_NS="${TGT_NS:-ktayl-core}"; TGT_DB="${TGT_DB:-ktayl_core}"
TGT_POD="${TGT_POD:-$(kubectl get pod -n "$TGT_NS" -l cnpg.io/instanceRole=primary -o jsonpath='{.items[0].metadata.name}')}"
echo "source=$SRC_NS/$SRC_POD:$SRC_DB  target=$TGT_NS/$TGT_POD:$TGT_DB  mode=$MODE"

SRC_SQL="COPY (
  SELECT id, policy_number, holder_name, product_code,
         CASE lower(status)
           WHEN 'draft' THEN 'DRAFT' WHEN 'submitted' THEN 'SUBMITTED' WHEN 'active' THEN 'ACTIVE'
           WHEN 'amended' THEN 'AMENDED' WHEN 'cancelled' THEN 'CANCELLED' WHEN 'expired' THEN 'EXPIRED'
           WHEN 'rejected' THEN 'REJECTED'
           WHEN 'terminated' THEN 'CANCELLED'   -- legacy PAS status → nearest ktayl-core state
           WHEN 'suspended' THEN 'AMENDED'       -- legacy PAS status → revisit if prod has these
           ELSE NULL END AS status,
         effective_date::date, expiry_date::date, created_at, updated_at
  FROM policies
) TO STDOUT WITH (FORMAT csv)"
CSV=$(kubectl exec -n "$SRC_NS" "$SRC_POD" -- psql -U "$SRC_USER" -d "$SRC_DB" -At -c "$SRC_SQL")
echo "exported $(printf '%s\n' "$CSV" | grep -c .) source rows"

if [ "$MODE" = "dry-run" ]; then
  echo "--- would import (policy_number | mapped status); existing in target are skipped by ON CONFLICT ---"
  printf '%s\n' "$CSV" | awk -F, '{print "  "$2" | "$5}'
  kubectl exec -n "$TGT_NS" "$TGT_POD" -c postgres -- psql -U postgres -d "$TGT_DB" -At \
    -c "select 'target now: '||count(*)||' policies, '||(select count(*) from policy.policy_audit_log where action='BACKFILL')||' backfill-audit rows' from policy.policy;"
  exit 0
fi

APPLY="CREATE TEMP TABLE _bf (id uuid, policy_number text, holder_name text, product_code text,
  status text, effective_date date, expiry_date date, created_at timestamptz, updated_at timestamptz);
\copy _bf FROM STDIN WITH (FORMAT csv)
$CSV
\.
INSERT INTO policy.policy (id, policy_number, holder_name, product_code, status, effective_date, expiry_date, created_at, updated_at)
  SELECT id, policy_number, holder_name, product_code, status, effective_date, expiry_date, created_at, updated_at FROM _bf
  ON CONFLICT (policy_number) DO NOTHING;
INSERT INTO policy.policy_audit_log (policy_id, action, from_status, to_status, actor_id, reason, at)
  SELECT p.id, 'BACKFILL', p.status, p.status, 'policy-fold-migration',
         'ADR-014 Phase 2 backfill from ktayl-policy-service', now()
  FROM policy.policy p JOIN _bf b ON b.id = p.id
  WHERE NOT EXISTS (SELECT 1 FROM policy.policy_audit_log a WHERE a.policy_id = p.id AND a.action='BACKFILL');
SELECT 'after: '||count(*)||' policies, '||(select count(*) from policy.policy_audit_log where action='BACKFILL')||' backfill-audit rows' FROM policy.policy;"
printf '%s' "$APPLY" | kubectl exec -i -n "$TGT_NS" "$TGT_POD" -c postgres -- psql -U postgres -d "$TGT_DB" -v ON_ERROR_STOP=1 | tail -4
