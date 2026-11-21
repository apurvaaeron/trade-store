# ADR-0002: PostgreSQL for current trade state, MongoDB for the audit log

**Status:** Accepted

## Context
The brief asks for both SQL and NoSQL. The store needs strict, transactional rules on current state, plus a complete,
schema-flexible record of every message received (including malformed and rejected ones) for audit and support.

## Decision
- **PostgreSQL** `trades` table: one row per trade (latest version), primary key `trade_id`, managed by Flyway.
- **MongoDB** `trade_audit` collection: one immutable document per received message with payload, channel, outcome,
  rejection reason and timestamp.

## Consequences
- Relational constraints and atomic conditional upserts where correctness matters; append-only, flexible documents
  where volume and schema variety matter.
- The two writes are not in one transaction. PostgreSQL is the source of truth; an audit failure is logged and
  does not undo an accepted trade.
- **Production follow-up:** transactional outbox (write the audit event in the PostgreSQL transaction, relay to
  MongoDB / Kafka with Debezium) to make the audit trail guaranteed rather than best-effort.
