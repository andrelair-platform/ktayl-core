# Changelog

## [0.1.1](https://github.com/andrelair-platform/ktayl-core/compare/ktayl-core-v0.1.0...ktayl-core-v0.1.1) (2026-11-21)


### Features

* **billing:** BILL-010 Spring Modulith skeleton + boundary test + CI + Dockerfile ([#1](https://github.com/andrelair-platform/ktayl-core/issues/1)) ([d4aed75](https://github.com/andrelair-platform/ktayl-core/commit/d4aed75c42da11d3a91435fb5ac4968793c13e2c))
* **billing:** BILL-011 ingest bound premium from UW bound-risk event (ADR-003) ([#4](https://github.com/andrelair-platform/ktayl-core/issues/4)) ([174246f](https://github.com/andrelair-platform/ktayl-core/commit/174246f4df11491ab9cfcbbd64d58eab9a06fa2e))
* **billing:** BILL-012 — raise premium invoice + installment schedule ([#7](https://github.com/andrelair-platform/ktayl-core/issues/7)) ([301bc4e](https://github.com/andrelair-platform/ktayl-core/commit/301bc4ec9778583992da7d0919a5d6052505aaf3))
* **billing:** BILL-013 Stripe payment — initiate + webhook capture (dormant until keys) ([#45](https://github.com/andrelair-platform/ktayl-core/issues/45)) ([40d8a1e](https://github.com/andrelair-platform/ktayl-core/commit/40d8a1e224e1f90efbaf20ab959215435816dca7))
* **billing:** BILL-013c failed-payment → dunning → lapse (non-payment path) ([#49](https://github.com/andrelair-platform/ktayl-core/issues/49)) ([051379a](https://github.com/andrelair-platform/ktayl-core/commit/051379a1c5a27c9f713bae004befb28ec4ab37a4))
* **billing:** BILL-014 GL posting to ERPNext via a transactional outbox (dormant until creds) ([#47](https://github.com/andrelair-platform/ktayl-core/issues/47)) ([416aeee](https://github.com/andrelair-platform/ktayl-core/commit/416aeee64d0ecea848d7c404114e57f79b545e83))
* **billing:** split premium into gross/net/commission (BILL-012b) ([#80](https://github.com/andrelair-platform/ktayl-core/issues/80)) ([17f2154](https://github.com/andrelair-platform/ktayl-core/commit/17f2154651bd16dc0bc5bcbb8140d4b7683dac25))
* **claimsgl:** claims GL seam — event → outbox → ERPNext JE (CLM-FIN-01, ADR-021) ([#94](https://github.com/andrelair-platform/ktayl-core/issues/94)) ([65c4106](https://github.com/andrelair-platform/ktayl-core/commit/65c41068c4790d1e4dbe132f0c7a54ae72bc2e59))
* **complaints:** CMPL-010 — module skeleton + owned schema + boundary guard ([#27](https://github.com/andrelair-platform/ktayl-core/issues/27)) ([5b74c04](https://github.com/andrelair-platform/ktayl-core/commit/5b74c04a0e98cd983116a22796d0039baf5b5c19))
* **complaints:** CMPL-011 Complaint aggregate + register + ComplaintRegistered event (hexagonal) ([#30](https://github.com/andrelair-platform/ktayl-core/issues/30)) ([25201f8](https://github.com/andrelair-platform/ktayl-core/commit/25201f8944baeb94e8ae83b4640baff7112942c3)), closes [#21](https://github.com/andrelair-platform/ktayl-core/issues/21)
* **complaints:** CMPL-012 lifecycle + state-machine guards + ack-due clock ([#33](https://github.com/andrelair-platform/ktayl-core/issues/33)) ([04084f7](https://github.com/andrelair-platform/ktayl-core/commit/04084f77acceb561a8adfd01890ba95f6dded70c))
* **complaints:** CMPL-013 published API + REST + SSO + exception mapping ([#35](https://github.com/andrelair-platform/ktayl-core/issues/35)) ([845bdb7](https://github.com/andrelair-platform/ktayl-core/commit/845bdb7c3c8d1fcaac1141994524c761c494360d)), closes [#23](https://github.com/andrelair-platform/ktayl-core/issues/23)
* **finance:** add accounting-period close/reopen with immutable closing audit (FINCLOSE-02) ([82d03d8](https://github.com/andrelair-platform/ktayl-core/commit/82d03d8bc9130a69c8b412d2c162fc7d4559bc2f))
* **finance:** add period-close GL reconciliation + premium register module (FINCLOSE-01) ([02feb83](https://github.com/andrelair-platform/ktayl-core/commit/02feb837da27031473bfa5cecc8121a69b760e93))
* **finance:** close dashboard + GL outbox-drift metrics (FINCLOSE-03 app) ([#79](https://github.com/andrelair-platform/ktayl-core/issues/79)) ([f187148](https://github.com/andrelair-platform/ktayl-core/commit/f18714891b4af34b2563f43648d14ae349a8032a))
* **observability:** OTel triad foundation — metrics + traces + logs (ADR-012) ([#28](https://github.com/andrelair-platform/ktayl-core/issues/28)) ([2dafc05](https://github.com/andrelair-platform/ktayl-core/commit/2dafc05ea3f96f55bea8216541e4118bad2e9414))
* **policy-fold:** Phase 2 backfill tool + dev backfill (ADR-014) ([#55](https://github.com/andrelair-platform/ktayl-core/issues/55)) ([b5df146](https://github.com/andrelair-platform/ktayl-core/commit/b5df14636ceadb0b749f6ccc304697360a619187))
* **policy:** fold the PAS into ktayl-core as the policy module (Phase 1, bind-path core) ([#50](https://github.com/andrelair-platform/ktayl-core/issues/50)) ([9ed31e4](https://github.com/andrelair-platform/ktayl-core/commit/9ed31e4bf0a751af9534f27a43c1c54e51f81725))
* **policy:** list + keyset pagination + DRAFT update (BF-POL-04) ([#54](https://github.com/andrelair-platform/ktayl-core/issues/54)) ([42d2dd8](https://github.com/andrelair-platform/ktayl-core/commit/42d2dd8dc644d1ebc2092910fe4df5a2747eccd7))
* **screening:** RC-02 S1 — ktayl-core screening module ([#61](https://github.com/andrelair-platform/ktayl-core/issues/61)) ([6f5dbbd](https://github.com/andrelair-platform/ktayl-core/commit/6f5dbbd7925e83e95c9cda5b8d124e16826dd6d4))
* **screening:** RC-02 S2+S4 — bind + payment sanctions gates ([#64](https://github.com/andrelair-platform/ktayl-core/issues/64)) ([18969f1](https://github.com/andrelair-platform/ktayl-core/commit/18969f1b8c2e438203de58c915e893af3daa64eb))
* **security:** multi-issuer M2M token validation (ADR-016) ([#52](https://github.com/andrelair-platform/ktayl-core/issues/52)) ([864e810](https://github.com/andrelair-platform/ktayl-core/commit/864e810ac76c01567e01351eac5c9583c6f6fc31))
* **submission:** attach oauth2 client-credentials bearer on underwriting handoff (sub-02 prod) ([#69](https://github.com/andrelair-platform/ktayl-core/issues/69)) ([604c7d3](https://github.com/andrelair-platform/ktayl-core/commit/604c7d360be93520cdfa62ba454d56220c08b485))
* **submission:** SUB-01 — Submission Hub module (broker intake → triage → route) ([#65](https://github.com/andrelair-platform/ktayl-core/issues/65)) ([4bbc7cd](https://github.com/andrelair-platform/ktayl-core/commit/4bbc7cd24b394a77e3907b36b7945b93f32c56fd))
* **submission:** SUB-02 — hand routed submissions off to underwriting (dev) ([#68](https://github.com/andrelair-platform/ktayl-core/issues/68)) ([f9c87b6](https://github.com/andrelair-platform/ktayl-core/commit/f9c87b6a0ee92cee008e8e4012e00fb567ccc317))


### Bug Fixes

* **billing:** ERPNext JE needs a Party on the receivable leg + cheque_date ([#48](https://github.com/andrelair-platform/ktayl-core/issues/48)) ([01cdbfc](https://github.com/andrelair-platform/ktayl-core/commit/01cdbfc8836d7f025a4312d20f227f218d8227ec))
* **billing:** gate the invoice API to Finance + commission-split QA gate ([#81](https://github.com/andrelair-platform/ktayl-core/issues/81)) ([771e6dd](https://github.com/andrelair-platform/ktayl-core/commit/771e6dd9d644679950e8178db85157b3947826fb))
* **billing:** gate UW consumer on billing.nats.url, not @ConditionalOnBean ([#41](https://github.com/andrelair-platform/ktayl-core/issues/41)) ([9684b71](https://github.com/andrelair-platform/ktayl-core/commit/9684b71f61c86f5ec2cfa265508dcdc0baf6559d))
* **billing:** REQUIRES_NEW on the AFTER_COMMIT auto-invoice listener ([#42](https://github.com/andrelair-platform/ktayl-core/issues/42)) ([2225e1b](https://github.com/andrelair-platform/ktayl-core/commit/2225e1b87da467bcf6bae564e8538599449e2422))
* **billing:** webhook returns 400 (not 500/NPE) on a missing Stripe-Signature ([#46](https://github.com/andrelair-platform/ktayl-core/issues/46)) ([d2aa2aa](https://github.com/andrelair-platform/ktayl-core/commit/d2aa2aae6bd8d952c5d381e41d400ab3ff724d26))
* **deps:** bump Spring Boot 3.4.1→3.5.16 + Modulith 1.4.13, pin tomcat 10.1.60 ([#3](https://github.com/andrelair-platform/ktayl-core/issues/3)) ([66a5b17](https://github.com/andrelair-platform/ktayl-core/commit/66a5b17101a02e2491da93fb61f0c5debf93c0e5))
* **diagrams:** self-contained c4 svg so it opens standalone ([#11](https://github.com/andrelair-platform/ktayl-core/issues/11)) ([192cf20](https://github.com/andrelair-platform/ktayl-core/commit/192cf20027878d0e94b66e05a0a4787a990f34ed))
* **oidc:** bake minicloud CA into the JVM truststore for the Authentik JWKS fetch ([#38](https://github.com/andrelair-platform/ktayl-core/issues/38)) ([c2e0f86](https://github.com/andrelair-platform/ktayl-core/commit/c2e0f862e92a82f96c16b0ebcd8c2137ccb16a50))
* **oidc:** base64 the CA build-arg (multi-line PEM was split by buildx) ([#39](https://github.com/andrelair-platform/ktayl-core/issues/39)) ([c7055a2](https://github.com/andrelair-platform/ktayl-core/commit/c7055a2de6e15abcb275b9edd6fd08ffe0d8d31c))
* **policy:** GET /v1/policies returns the PAS list envelope, not a single object ([#51](https://github.com/andrelair-platform/ktayl-core/issues/51)) ([c20e90a](https://github.com/andrelair-platform/ktayl-core/commit/c20e90aadfd2b9245cd98ee217b00b342402d2c0))
* **policy:** oversized input is 422, not a 500 (QA gate finding) ([#56](https://github.com/andrelair-platform/ktayl-core/issues/56)) ([e42bf7d](https://github.com/andrelair-platform/ktayl-core/commit/e42bf7d1aa7dac78a22fb15b2f64f8b174a486a1))
* **screening:** YenteClient force HTTP/1.1 (yente uvicorn rejects JDK h2c upgrade) ([#63](https://github.com/andrelair-platform/ktayl-core/issues/63)) ([6fce393](https://github.com/andrelair-platform/ktayl-core/commit/6fce393dc4089a68032fc34a2a092e5a8a0ee72e))
* **screening:** YenteClient sends a Content-Length body (yente rejects chunked) ([#62](https://github.com/andrelair-platform/ktayl-core/issues/62)) ([2cb7fb3](https://github.com/andrelair-platform/ktayl-core/commit/2cb7fb3270258bad82a0c0b87e0f27d71e0534b4))
* **security:** accept-risk Spring CVE-2026-47884/47890 (not reachable; no 6.2.x fix) ([#32](https://github.com/andrelair-platform/ktayl-core/issues/32)) ([5f533e0](https://github.com/andrelair-platform/ktayl-core/commit/5f533e0ba4f18224f48a2dd5ad862da8e7e92ac5))
* **security:** drop stray :80 from the default underwriting trusted-issuer (exact-match 401) ([#67](https://github.com/andrelair-platform/ktayl-core/issues/67)) ([06fbafe](https://github.com/andrelair-platform/ktayl-core/commit/06fbafe754f2bffd8f4c904c56ebec4917838699))

## Changelog

All notable changes to ktayl-core are documented here.

This file is maintained by [release-please](https://github.com/googleapis/release-please).
