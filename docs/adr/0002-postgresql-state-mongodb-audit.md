# ADR-0002: PostgreSQL for current trade state, MongoDB for the audit log

**Status:** Accepted

## Context
The brief asks for both SQL and NoSQL. The store needs strict, transactional rules on current state, plus a best-effort,
schema-flexible audit record for decoded submissions that reach ingestion, including accepted and business-rejected ones.
REST payloads that cannot be deserialized return 400 before ingestion; Kafka messages that cannot be deserialized bypass
the audit and go to the dead-letter topic.

## Decision
- **PostgreSQL** `trades` table: one row per trade (latest version), primary key `trade_id`, managed by Flyway.
- **MongoDB** `trade_audit` collection: a best-effort immutable document for each decoded `TradeRequest` passed to
  ingestion, with decoded payload, channel, outcome, rejection reason and timestamp.

## Consequences
- Relational constraints and atomic conditional upserts where correctness matters; append-only, flexible documents
  where volume and schema variety matter.
- The two writes are not in one transaction. PostgreSQL is the source of truth; an audit failure is logged and
  does not undo an accepted trade.
- REST payloads and Kafka records that fail deserialization do not reach ingestion and therefore have no audit event;
  undecodable Kafka records are routed to `trades.inbound.dlt`.
- **Production follow-up:** transactional outbox (write the audit event in the PostgreSQL transaction, relay to
  MongoDB / Kafka with Debezium) to make the audit trail guaranteed rather than best-effort.
