# Epics & Stories — ktayl-policy-service

**Status:** Active
**Sprint:** CERT-1 M1-M2
**Total SP:** 30 | Done: 5 SP | Remaining: 25 SP

---

## Epic 1: Policy Lifecycle Core

**Goal:** A souscripteur can create, read, update, and lifecycle a policy through all states. Every transition is immutably logged (DORA Art.9).

**Epic acceptance:** `POST /v1/policies` → 201; `POST /submit` + `POST /activate` → status=active; `GET /history` → audit rows exist with actor_id + reason.

**CdCF refs:** BF-POL-01, BF-POL-02, BF-POL-03

### Story 1.1: Repo scaffold and CI skeleton

Go module, Makefile, Dockerfile (distroless), golangci-lint + govulncheck + gosec CI, release-please, Docusaurus website. **Status: done**

### Story 1.2: Domain model and PostgreSQL schema

Policy/Coverage/Premium structs with Validate(), V1__init_schema.sql (uuid-ossp, constraints, indexes), postgres/* repository implementations (pgx/v5). Monetary amounts as int64 eurocents. **Status: done**

### Story 1.3: Policy REST API — CRUD and OpenAPI spec

`POST /v1/policies` (201, DRAFT), `GET /v1/policies/:id`, `GET /v1/policies` (cursor pagination), `PUT /v1/policies/:id`, `DELETE /v1/policies/:id` (soft-cancel DRAFT only). OpenAPI 3.1 at `api/openapi.yaml`. RFC 7807 error responses. `validator/v10` request validation. Domain service layer `internal/domain/policy_service.go`. **Status: in-progress**

### Story 1.4: Policy state machine and DORA audit log

`Transition(current, event) (Status, error)` pure function, transition table map. `policy_audit_log` table (V2__audit_log.sql). Every transition: BEGIN → UPDATE policies + INSERT audit_log → COMMIT. Endpoints: `POST /v1/policies/:id/submit`, `/activate`, `/cancel`. `GET /v1/policies/:id/history`. **Status: ready-for-dev**

---

## Epic 2: Documents and Integrations

**Goal:** An assuré can download a PDF attestation. Downstream services receive lifecycle events without polling.

**Epic acceptance:** `POST /v1/policies/:id/documents/attestation` returns `{url, expires_at}` for ACTIVE policy; NATS stream `POLICY_EVENTS` contains `policy.activated` CloudEvent.

**CdCF refs:** BF-POL-05, BF-POL-06

### Story 2.1: Policy attestation PDF generation and MinIO storage

`GenerateAttestation()` via `go-pdf/fpdf` (no CGO). MinIO bucket `policy-documents` (7-year lifecycle, ACPR Art.L113-5). `policy_documents` table (V3__documents.sql). `POST /v1/policies/:id/documents/attestation`, `GET /v1/policies/:id/documents`. Presigned URL TTL 1h. Status guard: ACTIVE or AMENDED only. **Status: ready-for-dev**

### Story 2.2: NATS JetStream event publisher

`Publisher.Connect()` + `Publish()` with 3× backoff (5s/25s/125s). Stream `POLICY_EVENTS` (subjects: `policy.>`, retention 7 days). CloudEvents 1.0 envelope per event type. Publish after `tx.Commit()` (goroutine). NATS health in `/healthz`. Unit tests with embedded nats-server/v2. **Status: ready-for-dev**

---

## Epic 3: Security

**Goal:** Every endpoint except `/healthz` is protected by Authentik JWT. Scopes enforce least privilege. `actor_id` in audit log = JWT `sub` claim.

**Epic acceptance:** No token → 401. Valid token, wrong scope → 403. `sub` claim available in handler context.

**CdCF refs:** BF-POL-07, CdCF §7.3

### Story 3.1: Authentik M2M JWT middleware and JWKS validation

`internal/api/middleware/auth.go` — JWKS via `keyfunc/v3`, 5-min cache. `authz.go` — `RequireScope()` factory. `policy:read` for GETs, `policy:write` for POST/PUT/DELETE. `sub` claim in context → `actor_id` in audit log. 8 table-driven test cases. Authentik M2M provider setup documented in PR. **Status: ready-for-dev**

---

## Epic 4: Quality and Deployment

**Goal:** CI is green on every push. Integration tests cover the full happy path. Service runs on minicloud via ArgoCD.

**Epic acceptance:** golangci-lint 0 errors, coverage ≥ 70%. REC-POL-01 smoke test passes from Mac. ArgoCD Healthy+Synced.

### Story 4.1: Unit test suite and golangci-lint CI gate

`.golangci.yml` with govet, errcheck, staticcheck, unused, gocritic, gofmt. CI golangci-lint step before go test. Coverage gate 70% on domain + api packages. `make lint` target. **Status: ready-for-dev**

### Story 4.2: Integration tests with testcontainers

`tests/integration/` — testcontainers PostgreSQL 16 + embedded NATS + `httptest.Server`. Full lifecycle: create → submit → activate → GET → cancel. `make test-integration` target. CI job on PR to staging. **Status: ready-for-dev**

### Story 4.3: Kubernetes manifests and ArgoCD Application

Kustomize base + minicloud-1/{dev,staging,prod} overlays. `ktayl-postgres` StatefulSet (set-hog, 5Gi Longhorn). ESO ExternalSecret from `secret/ktayl/policy-service/app`. NetworkPolicy (ingress nginx, egress postgres/NATS/Authentik/MinIO). ArgoCD Application in minicloud-gitops. `ktayl` ns added to AppProject. `promote-dev` CI job. **Status: ready-for-dev**
