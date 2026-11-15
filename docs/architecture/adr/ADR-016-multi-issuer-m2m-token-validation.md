# ADR-016 — Multi-issuer M2M token validation (how services authenticate INTO ktayl-core)

**Status:** Accepted (2026-11-15) · **Owner:** SA/TL · **Context:** ktayl-core's first service-to-service
(M2M) consumer — underwriting's policy-bind client (ADR-014 Phase 3) — was rejected with `401` even though
its token was signed by a key ktayl-core trusts.

## Context
ktayl-core is an OIDC resource-server. It originally validated tokens with Spring's strict
`issuer-uri = https://auth.devandre.sbs/application/o/ktayl-core/`, which enforces an **exact `iss` match**.

Its consumers so far were all the *ktayl-core* Authentik application (human SSO via the browser → `iss` =
`.../ktayl-core/`) — so strict `issuer-uri` worked. Underwriting, binding risk into the folded-in policy
module, authenticates **machine-to-machine** with its own Authentik provider (`ktayl-underwriting`), whose
tokens carry a **different `iss`**: `http://authentik-server.authentik.svc:80/application/o/ktayl-underwriting/`
(note two differences from the SSO issuer — a different app slug **and** the *internal* cluster host, because
an in-cluster client-credentials call mints against `authentik-server.authentik.svc`, not the external host).

Empirically (decoded live): underwriting's token is **signed by the same key** as ktayl-core's — Authentik's
providers here share one signing certificate, so the `kid` in underwriting's token is present in ktayl-core's
JWKS. The **only** thing failing was the strict `iss` equality check, not the signature.

## Decision
Validate by **JWKS signature + a trusted-issuer *allowlist*** — not strict single-issuer:

1. Resource-server uses **`jwk-set-uri`** (`.../ktayl-core/jwks/`) → validates the signature against the
   shared Authentik key (lazy fetch; no eager startup discovery). This is also what the legacy Go PAS did.
2. A custom `OAuth2TokenValidator<Jwt>` (`TrustedIssuerValidator`) checks the raw `iss` claim against an
   **allowlist** (`AUTHENTIK_TRUSTED_ISSUERS`, comma-separated), combined with the default timestamp
   validators via `DelegatingOAuth2TokenValidator`.
3. The allowlist holds the **exact** issuer strings (trailing slash included; the internal-host form for
   in-cluster M2M tokens): ktayl-core's own (human SSO) + each M2M consumer. Dev default =
   `…/ktayl-core/` + `…svc:80/application/o/ktayl-underwriting/`.

**Rejected alternatives:**
- *Drop the `iss` check (signature-only, like the PAS).* Simpler, but blind-trusts **any** Authentik-signed
  token (every app shares the signing key) — a token minted for another service would be accepted here. The
  allowlist keeps `iss` defence-in-depth for a config-line cost.
- *Fix it in Authentik (shared-issuer mode / a ktayl-core-provider M2M client).* Changes shared identity
  config + forces external-host minting + CA trust in the consumer; more blast radius than a resource-server
  config change, and ktayl-core would still be strict-single-issuer (no room for a 2nd consumer).
- *`JwtIssuerAuthenticationManagerResolver` (per-issuer decoders).* True multi-tenant, but heavier and aimed
  at *independent* issuers with *different* keys — overkill when one shared key signs them all.

## Consequences
- **Positive:** underwriting (and future internal M2M consumers) authenticate into ktayl-core with their own
  identity; the DORA audit records the real actor; `iss` is still validated (defence in depth); adding a
  consumer is a one-line `AUTHENTIK_TRUSTED_ISSUERS` change, no code. Faster startup (no eager discovery).
- **Negative / accepted:** the allowlist is maintained per environment (dev/prod issuers differ — prod M2M
  mints against the external host). Authorization is still `.authenticated()` on the policy bind endpoints +
  **network policy** (only underwriting may reach them) — a stricter `SCOPE_policy:write` gate (as the PAS
  had `readScope`/`writeScope`) is an available hardening, deferred.
- **Security posture:** identity = a token signed by our Authentik **and** from an allow-listed issuer;
  access = per-endpoint authz (groups/authenticated) + default-deny netpols. Coherent with the BYOD /
  zero-trust model (`platform-vs-information-system.md`). Cert evidence: **Bloc 2 (concevoir & développer /
  sécuriser)**.

Related: ADR-014 (the fold), ADR-015 (the policy module), the SPEC Phase 3 (repoint underwriting),
[[feedback_crossservice_m2m_auth_wiring]] (the 4-things-must-align pattern — signing-key/kid confirmed here).
