# Changelog

## [0.1.1](https://github.com/andrelair-platform/ktayl-core/compare/ktayl-core-v0.1.0...ktayl-core-v0.1.1) (2026-11-14)


### Features

* **billing:** BILL-010 Spring Modulith skeleton + boundary test + CI + Dockerfile ([#1](https://github.com/andrelair-platform/ktayl-core/issues/1)) ([d4aed75](https://github.com/andrelair-platform/ktayl-core/commit/d4aed75c42da11d3a91435fb5ac4968793c13e2c))
* **billing:** BILL-011 ingest bound premium from UW bound-risk event (ADR-003) ([#4](https://github.com/andrelair-platform/ktayl-core/issues/4)) ([174246f](https://github.com/andrelair-platform/ktayl-core/commit/174246f4df11491ab9cfcbbd64d58eab9a06fa2e))
* **billing:** BILL-012 — raise premium invoice + installment schedule ([#7](https://github.com/andrelair-platform/ktayl-core/issues/7)) ([301bc4e](https://github.com/andrelair-platform/ktayl-core/commit/301bc4ec9778583992da7d0919a5d6052505aaf3))
* **billing:** BILL-013 Stripe payment — initiate + webhook capture (dormant until keys) ([#45](https://github.com/andrelair-platform/ktayl-core/issues/45)) ([40d8a1e](https://github.com/andrelair-platform/ktayl-core/commit/40d8a1e224e1f90efbaf20ab959215435816dca7))
* **complaints:** CMPL-010 — module skeleton + owned schema + boundary guard ([#27](https://github.com/andrelair-platform/ktayl-core/issues/27)) ([5b74c04](https://github.com/andrelair-platform/ktayl-core/commit/5b74c04a0e98cd983116a22796d0039baf5b5c19))
* **complaints:** CMPL-011 Complaint aggregate + register + ComplaintRegistered event (hexagonal) ([#30](https://github.com/andrelair-platform/ktayl-core/issues/30)) ([25201f8](https://github.com/andrelair-platform/ktayl-core/commit/25201f8944baeb94e8ae83b4640baff7112942c3)), closes [#21](https://github.com/andrelair-platform/ktayl-core/issues/21)
* **complaints:** CMPL-012 lifecycle + state-machine guards + ack-due clock ([#33](https://github.com/andrelair-platform/ktayl-core/issues/33)) ([04084f7](https://github.com/andrelair-platform/ktayl-core/commit/04084f77acceb561a8adfd01890ba95f6dded70c))
* **complaints:** CMPL-013 published API + REST + SSO + exception mapping ([#35](https://github.com/andrelair-platform/ktayl-core/issues/35)) ([845bdb7](https://github.com/andrelair-platform/ktayl-core/commit/845bdb7c3c8d1fcaac1141994524c761c494360d)), closes [#23](https://github.com/andrelair-platform/ktayl-core/issues/23)
* **observability:** OTel triad foundation — metrics + traces + logs (ADR-012) ([#28](https://github.com/andrelair-platform/ktayl-core/issues/28)) ([2dafc05](https://github.com/andrelair-platform/ktayl-core/commit/2dafc05ea3f96f55bea8216541e4118bad2e9414))


### Bug Fixes

* **billing:** gate UW consumer on billing.nats.url, not @ConditionalOnBean ([#41](https://github.com/andrelair-platform/ktayl-core/issues/41)) ([9684b71](https://github.com/andrelair-platform/ktayl-core/commit/9684b71f61c86f5ec2cfa265508dcdc0baf6559d))
* **billing:** REQUIRES_NEW on the AFTER_COMMIT auto-invoice listener ([#42](https://github.com/andrelair-platform/ktayl-core/issues/42)) ([2225e1b](https://github.com/andrelair-platform/ktayl-core/commit/2225e1b87da467bcf6bae564e8538599449e2422))
* **billing:** webhook returns 400 (not 500/NPE) on a missing Stripe-Signature ([#46](https://github.com/andrelair-platform/ktayl-core/issues/46)) ([d2aa2aa](https://github.com/andrelair-platform/ktayl-core/commit/d2aa2aae6bd8d952c5d381e41d400ab3ff724d26))
* **deps:** bump Spring Boot 3.4.1→3.5.16 + Modulith 1.4.13, pin tomcat 10.1.60 ([#3](https://github.com/andrelair-platform/ktayl-core/issues/3)) ([66a5b17](https://github.com/andrelair-platform/ktayl-core/commit/66a5b17101a02e2491da93fb61f0c5debf93c0e5))
* **diagrams:** self-contained c4 svg so it opens standalone ([#11](https://github.com/andrelair-platform/ktayl-core/issues/11)) ([192cf20](https://github.com/andrelair-platform/ktayl-core/commit/192cf20027878d0e94b66e05a0a4787a990f34ed))
* **oidc:** bake minicloud CA into the JVM truststore for the Authentik JWKS fetch ([#38](https://github.com/andrelair-platform/ktayl-core/issues/38)) ([c2e0f86](https://github.com/andrelair-platform/ktayl-core/commit/c2e0f862e92a82f96c16b0ebcd8c2137ccb16a50))
* **oidc:** base64 the CA build-arg (multi-line PEM was split by buildx) ([#39](https://github.com/andrelair-platform/ktayl-core/issues/39)) ([c7055a2](https://github.com/andrelair-platform/ktayl-core/commit/c7055a2de6e15abcb275b9edd6fd08ffe0d8d31c))
* **security:** accept-risk Spring CVE-2026-47884/47890 (not reachable; no 6.2.x fix) ([#32](https://github.com/andrelair-platform/ktayl-core/issues/32)) ([5f533e0](https://github.com/andrelair-platform/ktayl-core/commit/5f533e0ba4f18224f48a2dd5ad862da8e7e92ac5))

## Changelog

All notable changes to ktayl-core are documented here.

This file is maintained by [release-please](https://github.com/googleapis/release-please).
