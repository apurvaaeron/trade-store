# ADR-0003: Enforce the version rule with an atomic conditional upsert

**Status:** Accepted

## Context
"Read the stored version, compare, then write" is racy: two consumers (or REST + Kafka) handling versions of the same
trade can interleave, and a lower version could overwrite a higher one.

## Decision
Keep the read-and-validate step for clear error messages, and make the write itself enforce the rule:

```sql
INSERT INTO trades (...) VALUES (...)
ON CONFLICT (trade_id) DO UPDATE SET ...
WHERE trades.version <= EXCLUDED.version
```

If no row is affected, a higher version won the race and the trade is rejected with `LowerVersionException`.

## Consequences
- Correct under concurrency without pessimistic locks or a JPA `@Version` column (which would clash with the business
  `version` field). Proven by `ConcurrentVersionsIT`.
- Ties the repository to PostgreSQL syntax (acceptable; the schema is PostgreSQL-specific anyway).
