# PRD — ktayl-policy-service

**Status:** Approved  
**Version:** 1.0  
**Author:** John (BMAD PM Agent) — retroactive generation 2026-08-16  
**Certification:** RNCP39583 CERT-1 M1-M2  
**Related:** CdCF §6.1 BF-POL-01 to BF-POL-05

---

## 1. Problem Statement

The ktayl-solution IS (simulated French insurance IS, modeled after HDI France) has no programmable policy ledger. Policies are created and tracked in ERPNext, which is a monolithic ERP — not a domain service. ERPNext cannot publish lifecycle events, enforce a controlled state machine, generate compliant attestation PDFs, or expose a clean API for the portal BFF and the claims service.

Without a dedicated policy microservice:
- The ktayl-portal (M7) has no stable REST contract to build on
- The ktayl-claims-service (M3-M5) cannot validate coverage on FNOL without polling ERPNext directly
- DORA Art.9 audit traceability does not exist — no immutable audit log
- ACPR Art.L113-5 compliance fails — no on-demand attestation document

---

## 2. Target Users

| User | Role | Interaction |
|---|---|---|
| **Souscripteur** | Underwriter at ktayl | Creates policies, submits for activation, processes amendments |
| **ktayl-portal BFF** | Backend-for-frontend (M7) | Calls policy API on behalf of authenticated broker users |
| **ktayl-claims-service** | Downstream microservice (M3) | Reads policy on FNOL to validate active coverage |
| **ktayl-ai-claims-assistant** | AI agent (M6) | Subscribes to `policy.activated` to pre-index attestation documents |
| **ACPR auditor (simulated)** | Regulator | Reviews audit log and attestation PDFs |

---

## 3. User Journeys

### Journey 1 — New Policy (Souscripteur)

```
1. Souscripteur POSTs policy data (holder, product, dates, coverages, premium)
2. Service validates and creates in DRAFT status
3. Souscripteur POSTs /submit → DRAFT → SUBMITTED
4. Underwriter review (implicit — not in this service, handled by portal workflow)
5. Souscripteur POSTs /activate → SUBMITTED → ACTIVE
6. NATS event `policy.activated` fires → claims-service + AI agent react
7. Assuré downloads attestation PDF via portal
```

### Journey 2 — Policy Amendment (Souscripteur)

```
1. Policy is ACTIVE
2. Souscripteur submits /amend with new coverage amounts
3. Policy moves ACTIVE → AMENDED
4. After confirmation: AMENDED → ACTIVE
5. New attestation generated on demand
```

### Journey 3 — FNOL Validation (ktayl-claims-service)

```
1. Claimant reports incident via portal
2. claims-service receives FNOL
3. claims-service GETs /v1/policies/:id to validate:
   - status == ACTIVE
   - effective_date <= incident_date <= expiry_date
   - coverage type covers the claim category
4. Proceeds with claim or rejects with policy-not-found / not-covered
```

### Journey 4 — Attestation Download (Portal → Assuré)

```
1. Assuré requests proof of insurance via portal
2. Portal BFF POSTs /v1/policies/:id/documents/attestation
3. Service generates PDF (policy #, holder, LOB, dates, coverage summary, timestamp)
4. PDF stored in MinIO, presigned URL (1h TTL) returned
5. Portal redirects browser to presigned URL → direct download
```

---

## 4. Functional Requirements

Mapped to CdCF §6.1:

| Ref | Requirement | Priority |
|---|---|---|
| BF-POL-01 | Create policy with holder, product code, effective/expiry dates, coverages | Must |
| BF-POL-02 | Enforce state machine: DRAFT → SUBMITTED → ACTIVE → AMENDED → CANCELLED/EXPIRED | Must |
| BF-POL-03 | Every state transition writes an immutable audit log row (DORA Art.9) | Must |
| BF-POL-04 | List policies with filters (status, LOB, holder) and cursor pagination | Should |
| BF-POL-05 | Generate attestation PDF on demand; store in MinIO; return presigned URL | Must |
| BF-POL-06 | Publish CloudEvents 1.0 to NATS JetStream on every transition | Must |
| BF-POL-07 | All endpoints protected by Authentik JWT (M2M client credentials + user OIDC) | Must |
| BF-POL-08 | Soft-cancel (status → CANCELLED) with mandatory reason code | Must |

### Transition table (BF-POL-02 detail)

```
DRAFT       → SUBMITTED  (souscripteur submits)
SUBMITTED   → ACTIVE     (underwriter approves)
SUBMITTED   → REJECTED   (underwriter rejects)
ACTIVE      → AMENDED    (PUT /amend, new coverage data)
AMENDED     → ACTIVE     (amendment confirmed)
ACTIVE      → CANCELLED  (POST /cancel, reason required)
ACTIVE      → EXPIRED    (automatic on expiry_date — batch job, not in this sprint)
```

### Reason code vocabulary (BF-POL-03)

`UNDERWRITER_APPROVED` · `UNDERWRITER_REJECTED` · `CUSTOMER_REQUEST` · `NON_PAYMENT` · `REGULATORY`

---

## 5. Non-Functional Requirements

| Category | Requirement |
|---|---|
| **DORA Art.9** | Every business decision (state transition) has actor_id + timestamp + reason in `policy_audit_log`. Non-repudiable, append-only. |
| **ACPR Art.L113-5** | Attestation PDF generated on demand for any ACTIVE policy. 7-year retention in MinIO (`policy-documents` bucket lifecycle rule). |
| **Performance** | p99 < 200ms for CRUD endpoints at 10 RPS sustained (minicloud cluster, 1 replica). |
| **Reliability** | NATS publish failures do not block the HTTP response. Retry up to 3× with backoff (5s/25s/125s). Log as ERROR. |
| **Security** | No endpoint except `/healthz` is unauthenticated. JWKS keys cached 5 min to prevent Authentik overload. |
| **Testability** | 70% coverage on `internal/domain` + `internal/api` packages. L2 integration test covers full lifecycle. |
| **Observability** | Structured JSON logs (slog). `/healthz` includes DB ping + NATS connection status. |

---

## 6. Out of Scope (this sprint)

| Item | Rationale |
|---|---|
| EXPIRED transition batch job | Requires scheduler; deferred to ops story post-M2 |
| ktayl-portal frontend | M7 milestone |
| ktayl-claims-service | M3-M5 milestone |
| PDF digital signature (Docuseal) | Only unsigned attestation in this sprint; DocuSeal = Phase 90 |
| Notification emails | Stalwart integration deferred |
| Multi-tenant / multi-company | ktayl-solution is single-tenant |
| Premium payment processing | ERPNext handles accounting; this service records premium metadata only |

---

## 7. Success Metrics

| Metric | Target | Sprint Gate |
|---|---|---|
| REC-POL-01 E2E smoke test | All 5 calls return 2xx | Required to close sprint |
| Coverage gate | ≥ 70% on domain + api packages | CI hard block |
| golangci-lint | 0 errors | CI hard block |
| DORA audit trail | Every transition has actor_id + reason | Part of REC-POL-01 |
| ArgoCD health | ktayl-policy-service Healthy+Synced | Required for S010 Done |

---

## 8. Open Questions / Assumptions

| # | Question | Assumption |
|---|---|---|
| 1 | Does the underwriter approval step have a UI in this sprint? | No — activate endpoint is called directly (portal UI = M7) |
| 2 | Is `actor_id` the Authentik `sub` claim or a ktayl user UUID? | Authentik `sub` claim — no separate user registry in this sprint |
| 3 | Should the EXPIRED batch job be a separate Go binary or a CronJob? | CronJob; deferred post-M2 |
| 4 | MinIO endpoint: internal cluster DNS or Tailscale? | Internal cluster DNS (`minio.minio.svc.cluster.local:9000`) |
