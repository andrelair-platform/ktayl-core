# Policy PAS — legacy design artifacts (preserved from the retired ktayl-policy-service)

These are the **BMAD planning artifacts of the original Go `ktayl-policy-service`** (the Policy
Administration System), preserved here when that service was **folded into ktayl-core** as the `policy`
module (ADR-014) and retired (Phase 5). They lived in the Go repo's **git-ignored `_bmad-output/`** working
area, so they existed only in a local clone — moving them here keeps the design history from being lost when
the repo is archived. This directly satisfies the fold requirement: *ktayl-core inherits the PAS's PRD /
architecture / epics so nothing of the design is discarded with the code.*

| File | What it is |
|---|---|
| `prd.md` | the PAS product requirements (`BF-POL-01..08`) — the source of the requirement set carried into the module |
| `architecture.md` | the PAS technical spine (state machine, data model, auth, events) — the source for ADR-015 |
| `epics.md` | the PAS epic/story map |
| `sprint-status.yaml` | the PAS engineering status at retirement |

## These are HISTORY, not the live design
The **current** design of record for the folded-in policy capability is in ktayl-core, not here:
- **`docs/architecture/adr/ADR-015-policy-module-folded-pas.md`** — the module design (derived from the PAS architecture above).
- **`docs/specs/spec-policy-fold/SPEC.md`** — the fold implementation contract + phases.
- **`docs/architecture/adr/ADR-014-...`** (the fold decision) and **ADR-016** (the M2M auth it required).

Where the module **deliberately diverged** from the PAS (read these artifacts with that in mind):
- **Coverages + premiums dropped** — orphan PAS schema (no write path); premiums are billing's SoR.
- **Status machine realigned** to the PAS *design* (DRAFT→SUBMITTED→ACTIVE→AMENDED→CANCELLED/EXPIRED/REJECTED),
  not the early live CHECK (draft/active/suspended/terminated).
- **`GET /v1/policies`** is the list envelope `{items,next_cursor}` (as the real Go handler returned), not a single object.
- Events/attestation are **need-first deferred** in the module (the PAS's `premiums`/attestation surfaces were dormant).
