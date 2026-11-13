# ADR-012 — Observability: the metrics + traces + logs triad (OpenTelemetry)

**Status:** Accepted (2026-11-13) · **Owner:** SA/TL · cross-cutting `shared/` foundation concern (applies
to every module). Prompted by the observability gap found at Phase-3 CMPL-010 (ktayl-core emitted only
partial metrics; no traces; unstructured logs).

## Context
A modular monolith that will grow to many modules + cross-module flows + external calls (NATS, ERPNext,
Stripe) needs the **full observability triad** — not just "it's up". Before this ADR, ktayl-core had:
- **metrics:** a `/actuator/prometheus` endpoint *configured* but **no `micrometer-registry-prometheus`
  dependency** → it produced nothing (broken);
- **traces:** none — even though the platform runs the OTLP collector + Tempo;
- **logs:** plain unstructured stdout, with **no trace correlation**.

The platform already provides all three backends (Prometheus · Tempo · Loki) via an OTLP collector
(`otelcol`). The app just has to emit, correctly, to them.

## Decision
Emit the **triad**, each to the platform backend it belongs to, with **trace correlation** tying them
together. Configured once in `shared` + `application.yml`, so **every module** is observable.

| Signal | How | Destination |
|---|---|---|
| **Metrics** | `micrometer-registry-prometheus` → `/actuator/prometheus` | platform **Prometheus** *scrapes* it (pull — the established model; not OTLP-pushed) |
| **Traces** | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` → OTLP | platform **otelcol** → **Tempo** |
| **Logs** | Spring Boot **structured logging** (`logging.structured.format.console=ecs`) → stdout | **Promtail** → **Loki** (12-factor #11: stdout event stream) |

**Correlation (the point of a *triad*, not three silos):** Micrometer Tracing injects `traceId`/`spanId`
into the MDC, and structured logging emits them as fields → **every log line links to its trace**, and a
trace links to its metrics by `service.name`. One incident → trace → the exact logs → the metrics around it.

**Attribution:** `spring.application.name=ktayl-core` becomes `service.name` on every signal.

**Config is env-overridable** (the same image runs dev/prod — 12-factor #3): `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`
(defaults to the in-cluster collector `http://otelcol-opentelemetry-collector.observability.svc:4318/v1/traces`),
`OTEL_TRACES_SAMPLING` (dev = `1.0` sample-all; prod lowers it), `LOG_STRUCTURED_FORMAT` (dev can set empty
for plain-text local logs).

## Why (vs the alternatives)
- **vs OTLP-push metrics:** the platform **scrapes** Prometheus (pull). Pushing metrics via OTLP too would
  duplicate the path. Keep metrics on the Prometheus scrape; only traces/logs need the push/stream.
- **vs an OTLP log appender:** the house standard (`twelve-factor.md` #11) is **stdout → Promtail → Loki**,
  not an in-app OTLP log exporter. Structured stdout + trace-correlation gives Loki everything without a
  second egress path or a logging SDK in the request path.
- **vs no tracing:** a modular monolith's whole value is cross-module in-process calls (and later events +
  external calls); without traces you cannot *see* a request cross a module boundary. Traces are how the
  modular monolith stays debuggable at scale.

## Consequences
- **Positive:** full triad from day one for **all** modules (billing + complaints + every future one);
  trace-correlated logs; the module-span view (`web → application → persistence`, later across modules +
  NATS) becomes visible in Tempo. Metrics actually work now (the missing registry is added).
- **Negative / accepted:** two new runtime deps + structured JSON console logs (right for k8s/Loki; local
  dev overrides `LOG_STRUCTURED_FORMAT` for readability). A tiny export overhead (sampled).
- **Tests unaffected:** unit tests have no context; `ModularityTests` is classpath-only; the JPA-slice
  integration test (`@DataJpaTest`) doesn't load tracing auto-config → no OTLP in tests.

## Deferred (deploy-time, not app-time)
The **egress NetworkPolicy** allowing ktayl-core → `observability:4318` is added **when the ktayl-core
workload is wired to gitops into a default-deny-egress namespace** (today only the NATS stream-init is in
gitops; the workload Deployment isn't). Premature to add a netpol for an undeployed workload — tracked as a
deploy-time follow-up. The app is OTLP-ready; it just needs the egress allow at deploy.

## Follow-ups
- Deploy-time: ktayl-core ns egress netpol → otelcol:4318 (gitops), when the workload lands.
- The CMPL-014 QA gate asserts the triad is live (logs emitted **and** a trace appears in Tempo).
