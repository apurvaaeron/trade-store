# Non-functional requirements

Targets are assumptions for a single trade store serving "thousands of trades". **Implemented** = in this
repository and covered by tests or the pipeline; **Design** = how it would be met in production.

| Category | Requirement | Target | How it is met | Status |
|---|---|---|---|---|
| Throughput | Sustained ingestion | ≥ 1,000 trades/s, bursts of 5,000 | Kafka partitions (3 by default) × listener concurrency; stateless app scales out horizontally | Smoke-tested in CI (`PerformanceSmokeIT`: 5,000 trades); load-tested locally with `perf/nfr-load.js` (k6, async mode, 1,000 req/s) |
| Latency | Ingestion to stored | p99 < 200 ms (REST sync path) | Single indexed primary-key read + one upsert per trade | Load-tested locally with `perf/nfr-load.js` (k6 threshold p99 < 200 ms); production would track `http_server_requests` |
| Ordering | Versions of the same trade | Processed in order | Kafka key = `tradeId`; conditional upsert as safety net | Implemented + `ConcurrentVersionsIT` |
| Consistency | Lower version never overwrites a higher one | 100% | `INSERT … ON CONFLICT … WHERE trades.version <= EXCLUDED.version` | Implemented + tests |
| Durability | No accepted trade lost | RPO = 0 for accepted trades | Producer `acks=all` + idempotence; offset committed after the DB transaction | Implemented |
| Delivery | Duplicates | At-least-once; same-version redelivery can overwrite a later same-version correction | Kafka key and conditional upsert prevent lower versions from overwriting higher ones; equal versions replace | Implemented with same-version caveat |
| Resilience | Poison / failing messages | Never block a partition | Retry 3× with back-off, then dead-letter topic; if publishing a business rejection fails, the original record is retried and dead-lettered rather than committed | Implemented + `TradeStoreFlowIT`, `RejectionPublishFailureIT` |
| Resilience | Async REST when Kafka is slow or down | Bounded wait, explicit outcome | `tradestore.async.publish-timeout` (5 s) and producer `max.block.ms` 5 s; `503` with `outcome` `UNKNOWN` or `FAILED` | Implemented + unit tests |
| Availability | Service | 99.9% | Stateless pods ≥ 2 replicas, readiness/liveness probes, managed Postgres/Mongo/Kafka with replicas | Probes implemented; rest design |
| Auditability | Decodable ingestion attempts traceable | Best-effort for `TradeRequest` submissions that reach ingestion and whose audit write succeeds | MongoDB events cover accepted and business-rejected decoded payloads; REST payloads that cannot be deserialized return 400 before ingestion, and undecodable Kafka messages bypass audit and go to DLT; `GET /api/v1/trades/{id}/audit` | Implemented, best effort |
| Correctness of "today" | Business date | Deterministic | Injected `Clock` with configurable zone; expiry also runs at start-up | Implemented |
| Observability | Metrics, health, logs | Accepted/rejected/expired counts, consumer lag | Actuator health (liveness/readiness), Prometheus endpoint, `tradestore.trades.received{channel,outcome,reason}`, `tradestore.trades.expired`, `tradestore.audit.failures` (alert on any increase); structured logs with `tradeId` | Implemented |
| Security | Supply chain | Build fails on critical or blocker vulnerabilities (blocker = HIGH) | Trivy scans of the built JAR and the image (HIGH, CRITICAL) are the enforced gate; OWASP Dependency-Check (CVSS ≥ 9) is optional and runs only with an `NVD_API_KEY`; dependency review blocks PRs that add HIGH/CRITICAL CVEs; CodeQL SAST and the SonarCloud quality gate run on every PR; Dependabot | Implemented |
| Security | Runtime | Least privilege | Non-root container; secrets via environment/secret store; OAuth2/JWT resource server and TLS in production | Partly (container); rest design |
| Maintainability | Test quality | Coverage ≥ 85% lines; mutation score ≥ 70% | JaCoCo gate in `mvn verify`; PIT job in CI | Implemented |
| Portability | Run anywhere | One command | `docker compose up`; Dev Container / Codespaces config | Implemented |
| Data | Schema evolution | Versioned, repeatable | Flyway migrations; Hibernate `validate` | Implemented |
| Scalability | Data growth | Fast expiry over millions of rows | Partial index on `maturity_date WHERE expired = false` | Implemented |

To demonstrate these targets on a local machine, see [local-run.md, section 7](../local-run.md#7-demonstrate-the-nfrs).
