# Design principles

The principles behind the trade store's code and architecture. Each one names where it is applied.

1. **One ingestion path for every channel.** REST and Kafka both call `TradeIngestionService`, so validation, storage, audit and metrics cannot drift apart.
2. **Enforce invariants in the database, not only in code.** The version rule is a single conditional upsert (`ON CONFLICT ... WHERE version <= new`), safe under concurrent consumers ([ADR-0003](adr/0003-atomic-conditional-upsert.md)).
3. **Reject, don't guess.** Strict parsing refuses fractional versions, impossible dates and unsafe IDs instead of coercing them (`TradeRequest`, strict deserializers).
4. **Make every rejection machine-readable.** One `TradeRejectedException` hierarchy with a `RejectionReason` drives the HTTP status (400 vs 422), RFC 9457 `ProblemDetail`, audit, metric tags and the `trades.rejected` topic.
5. **Separate business rejections from technical failures.** Business rejections are final and published; technical failures are retried, then dead-lettered so one bad record never blocks a partition.
6. **Preserve order where it matters.** Kafka messages are keyed by `tradeId`, giving per-trade ordering without global ordering costs ([ADR-0001](adr/0001-kafka-as-ingestion-backbone.md)).
7. **Don't lose accepted trades.** `acks=all`, idempotent producer, offsets committed only after the database write.
8. **One system of record.** PostgreSQL holds current state; MongoDB holds the best-effort audit trail and never blocks or undoes an accepted trade ([ADR-0002](adr/0002-postgresql-state-mongodb-audit.md)).
9. **Time is a dependency.** "Today" comes from an injected `Clock` in a configured zone, so date rules and expiry are deterministic in tests ([ADR-0004](adr/0004-clock-driven-expiry.md)).
10. **Idempotent, self-healing jobs.** Expiry runs daily and on startup and is safe to re-run, so a missed run catches up.
11. **Immutability and explicit dependencies.** Records for DTOs and configuration, constructor injection, final fields, no setters on the entity, no Lombok.
12. **Small, single-purpose classes.** Validator, store, ingestion, audit, expiry, publisher and listener each do one job and are tested in isolation.
13. **Schema as code.** Flyway owns the schema; Hibernate only validates it.
14. **Configuration over constants.** Topics, partitions, zone and schedule are typed `@ConfigurationProperties` with defaults, overridable per environment.
15. **Observable by default.** Health probes, Prometheus metrics tagged by channel, outcome and reason, and log values sanitised against injection.
16. **Secure by default.** Input allow-lists, non-root container, localhost-bound ports, and builds that fail on critical vulnerabilities.
17. **Automate the quality gates.** Unit, integration (Testcontainers), acceptance (Cucumber), concurrency, performance, mutation (PIT) and coverage (JaCoCo) tests all run in CI on every change.
18. **Make trade-offs explicit.** Assumptions, NFR targets, ADRs and known limitations are written down ([ASSUMPTIONS](ASSUMPTIONS.md), [NFR](NFR.md)).
