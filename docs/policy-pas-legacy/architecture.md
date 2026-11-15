# Architecture — ktayl-policy-service

**Status:** Approved  
**Version:** 1.0  
**Author:** Winston (BMAD Architect Agent) — retroactive generation 2026-08-16  
**ADRs:** inline below

---

## 1. System Context

```
┌─────────────────────────────────────────────────────────────────┐
│                       minicloud cluster                          │
│                                                                  │
│  ┌─────────────┐       REST+JWT      ┌──────────────────────┐  │
│  │ ktayl-portal │ ──────────────────▶│  ktayl-policy-service │  │
│  │   (M7, BFF) │                     │   (Go, chi, :8080)   │  │
│  └─────────────┘                     └─────────┬────────────┘  │
│                                                │                 │
│  ┌───────────────┐   GET /v1/policies/:id     │ pgx/v5          │
│  │ ktayl-claims  │ ──────────────────────────▶│                 │
│  │   service     │                             ▼                 │
│  │   (M3, Java)  │                    ┌─────────────────┐       │
│  └───────────────┘                    │  ktayl-postgres  │       │
│                                       │  (PostgreSQL 16) │       │
│  ┌───────────────┐   CloudEvents 1.0  └─────────────────┘       │
│  │ NATS JetStream│ ◀────────────────── policy_service.Publish()  │
│  │  (messaging)  │                                               │
│  └───────┬───────┘                    ┌─────────────────┐       │
│          │ policy.activated           │     MinIO        │       │
│          ▼                            │  (object store)  │       │
│  ┌───────────────┐                    │  policy-documents│       │
│  │  ktayl-ai     │ presigned URL ◀────└─────────────────┘       │
│  │claims-asst(M6)│                                               │
│  └───────────────┘                                               │
│                                                                  │
│  Authentik (OIDC/OAuth2) ── JWKS ──▶ JWT middleware (5-min cache)│
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Package Layout

```
ktayl-policy-service/
  cmd/server/
    main.go              # entry: config, wire deps, start HTTP server
  internal/
    api/
      router.go          # chi router setup, middleware registration
      handlers/
        policy_handler.go       # CRUD handlers
        transition_handler.go   # state machine endpoints
        document_handler.go     # PDF + MinIO endpoints
      middleware/
        auth.go          # JWT validation, JWKS cache
        authz.go         # scope-based authorization
        logger.go        # slog request logger
    domain/
      policy.go          # Policy struct + Validate()
      coverage.go        # Coverage struct
      premium.go         # Premium struct
      state_machine.go   # Transition(current, event) → (status, error)
      policy_service.go  # PolicyService: Create, Get, List, Update, Transition, Cancel
    repository/
      repository.go      # PolicyRepository, CoverageRepository, PremiumRepository, AuditLogRepository interfaces
      postgres/
        policy.go        # PolicyRepo (pgxpool)
        coverage.go      # CoverageRepo
        premium.go       # PremiumRepo
        audit_log.go     # AuditLogRepo (takes pgx.Tx — transactional)
    documents/
      pdf_generator.go   # GenerateAttestation() → []byte (go-pdf/fpdf)
      minio_store.go     # Store() → presignedURL (minio-go/v7)
    events/
      publisher.go       # Publisher.Connect(), Publish() with retry
      policy_events.go   # BuildCreatedEvent(), BuildActivatedEvent(), etc.
  db/
    migrations/
      V1__init_schema.sql       # policies, coverages, premiums tables
      V2__audit_log.sql         # policy_audit_log table
      V3__documents.sql         # policy_documents table
  api/
    openapi.yaml         # OpenAPI 3.1 spec (hand-written, authoritative)
  tests/
    integration/
      policy_test.go     # full lifecycle via httptest.Server + testcontainers
      testmain_test.go   # TestMain: postgres + NATS containers + server setup
      fixtures/          # request JSON fixtures
```

---

## 3. Architecture Decisions (ADRs)

### ADR-001: chi router (not FastHTTP or Fiber)

**Decision:** `github.com/go-chi/chi/v5`  
**Rationale:** chi uses `net/http` interfaces natively — all standard library middleware works without adapters. FastHTTP has custom types that break compatibility with `net/http/httptest` (required for L1 handler tests). Fiber adds CGO dependencies incompatible with distroless image.  
**Consequence:** Slightly lower raw throughput than Fiber, but handler tests are idiomatic and the perf gap is irrelevant at 10 RPS target.

### ADR-002: golang-migrate (Flyway naming convention)

**Decision:** `github.com/golang-migrate/migrate/v4` with file source, run at server startup  
**Rationale:** Flyway naming (`V1__init_schema.sql`) is familiar and already in use in the repo. golang-migrate supports it and runs migrations atomically. Alternative: Atlas ORM — rejected for adding code generation complexity.  
**Consequence:** Migration files must follow `V{N}__{description}.sql` pattern. Never edit a committed migration — add a new one.

### ADR-003: PostgreSQL amounts in eurocents (int64)

**Decision:** Monetary amounts (`insured_amount`, `deductible`, `premium.amount`) are stored and transmitted as **integer eurocents**, never as float/decimal.  
**Rationale:** IEEE 754 floating-point cannot represent 0.01 (1 eurocent) exactly. All rounding errors in insurance amounts have regulatory consequences. JSON API uses integer too — clients display `amount / 100`.  
**Consequence:** API consumers must handle eurocent integers. API documentation must be explicit.

### ADR-004: NATS publish after tx.Commit() (no outbox pattern)

**Decision:** Publish to NATS JetStream in a goroutine after `tx.Commit()` returns nil. No outbox table.  
**Rationale:** At this scale (single writer, 10 RPS), the window between commit and publish failure is acceptable. The outbox pattern adds a background goroutine, a polling loop, and schema complexity. If integration tests show event loss under load, add outbox in a follow-up story.  
**Consequence:** In a crash between commit and publish, the event is lost. Claims-service must handle eventual consistency (periodic reconciliation = future story).

### ADR-005: Distroless base image

**Decision:** `gcr.io/distroless/static-debian12:nonroot` as final image stage  
**Rationale:** No shell, no package manager — attack surface reduced to the binary and its shared libs. `CGO_ENABLED=0` + `-ldflags "-s -w"` produces a fully static binary that runs without glibc. govulncheck + gosec catch Go-level CVEs.  
**Consequence:** Cannot exec into the container for debugging. Use ephemeral debug containers (`kubectl debug`) when needed.

### ADR-006: go-pdf/fpdf (no CGO) for PDF generation

**Decision:** `github.com/go-pdf/fpdf` (pure Go)  
**Rationale:** wkhtmltopdf requires CGO and a full Chromium runtime — incompatible with distroless, adds 200 MB to image size. The attestation is a simple structured document (no CSS/HTML needed). fpdf produces ISO 32000-compliant PDFs adequate for ACPR compliance demonstration.  
**Consequence:** No rich HTML formatting. For advanced PDFs (client-facing policy documents), upgrade to a PDF generator with HTML support in a future sprint.

### ADR-007: Authentik M2M for service-to-service auth

**Decision:** OAuth2 client credentials flow (`client_id` + `client_secret`) via Authentik  
**Rationale:** No proprietary API key management. Tokens are JWTs — validated locally against JWKS (5-min cache) with zero network calls per request. Scope-based authorization (`policy:read`, `policy:write`) is clean and extensible.  
**Consequence:** Each calling service (portal BFF, claims-service) needs its own Authentik application. Manual setup documented in each PR. Client secrets stored in Vault `secret/ktayl/policy-service/`.

---

## 4. Data Model

### policies

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | uuid_generate_v4() |
| policy_number | TEXT UNIQUE NOT NULL | format: `KT-YYYY-{6 digits}` |
| holder_name | TEXT NOT NULL | |
| product_code | TEXT NOT NULL | e.g. `IARD-AUTO-RC`, `IARD-HAB-MRH` |
| status | TEXT NOT NULL | CHECK: draft/submitted/active/amended/cancelled/expired/rejected |
| effective_date | TIMESTAMPTZ NOT NULL | |
| expiry_date | TIMESTAMPTZ NOT NULL | CHECK: expiry > effective |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |

### coverages

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| policy_id | UUID FK → policies(id) ON DELETE CASCADE | |
| type | TEXT NOT NULL | e.g. `RC`, `DOMMAGES`, `VOL`, `BRIS_GLACE` |
| insured_amount | BIGINT NOT NULL | eurocents, > 0 |
| deductible | BIGINT NOT NULL DEFAULT 0 | eurocents, ≥ 0 |

### premiums

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| policy_id | UUID FK → policies(id) ON DELETE CASCADE | |
| amount | BIGINT NOT NULL | eurocents, > 0 |
| frequency | TEXT NOT NULL | CHECK: monthly/quarterly/annual |
| due_date | TIMESTAMPTZ NOT NULL | |
| paid_at | TIMESTAMPTZ | NULL = unpaid |

### policy_audit_log (V2)

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| policy_id | UUID NOT NULL | no FK (audit log must survive policy deletion) |
| from_status | TEXT NOT NULL | |
| to_status | TEXT NOT NULL | |
| actor_id | TEXT NOT NULL | Authentik `sub` claim |
| reason | TEXT NOT NULL | controlled vocabulary |
| occurred_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |

### policy_documents (V3)

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| policy_id | UUID NOT NULL | |
| type | TEXT NOT NULL | `attestation` |
| minio_key | TEXT NOT NULL | `policies/{id}/attestation-{timestamp}.pdf` |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |

---

## 5. API Surface (summary)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /healthz | None | Liveness + NATS/DB status |
| POST | /v1/policies | write | Create policy (DRAFT) |
| GET | /v1/policies | read | List with filters + cursor pagination |
| GET | /v1/policies/:id | read | Get policy with coverages + premium |
| PUT | /v1/policies/:id | write | Update mutable fields |
| POST | /v1/policies/:id/submit | write | DRAFT → SUBMITTED |
| POST | /v1/policies/:id/activate | write | SUBMITTED → ACTIVE |
| POST | /v1/policies/:id/cancel | write | ACTIVE → CANCELLED (reason required) |
| GET | /v1/policies/:id/history | read | Audit log |
| POST | /v1/policies/:id/documents/attestation | write | Generate PDF |
| GET | /v1/policies/:id/documents | read | List documents |

Full spec: `api/openapi.yaml` (hand-written, authoritative — code follows spec, not vice versa).

---

## 6. Infrastructure

| Component | Platform | Notes |
|---|---|---|
| Service | ktayl namespace, Longhorn volumes | Distroless, 1 replica dev / 2 prod |
| PostgreSQL | ktayl-postgres StatefulSet, set-hog | 5Gi Longhorn RWO, pinned to set-hog |
| Secrets | Vault `secret/ktayl/policy-service/app` → ESO → k8s Secret | DSN, NATS_URL, MINIO_*, JWKS_URL |
| GitOps | minicloud-gitops services/ktayl-policy-service/ | Kustomize base + dev/staging/prod overlays |
| Ingress | ktayl-policy.10.0.0.200.nip.io (dev/staging) | minicloud CA cert |
| Image | harbor.10.0.0.200.nip.io/library/ktayl-policy-service | cosign signed on staging/main |
