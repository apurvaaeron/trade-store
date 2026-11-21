# ADR-0001: Kafka as the ingestion backbone, keyed by trade id

**Status:** Accepted

## Context
Thousands of trades arrive from many upstream systems, out of order, and every version of a trade must be applied in
sequence. The brief asks for a streaming tool.

## Decision
Use Kafka topic `trades.inbound` with the message key = `tradeId`. All channels (Kafka producers, REST async) publish
to it; a synchronous REST endpoint calls the same ingestion service for callers that need an immediate answer.
Business rejections go to `trades.rejected`; technical failures go to `trades.inbound.dlt` after retries.

## Consequences
- Per-trade ordering through partition affinity; horizontal scale through partitions.
- Replay and back-pressure come for free; producers are decoupled from the store's availability.
- At-least-once delivery means the store must be idempotent (see ADR-0003).
- Alternatives considered: RabbitMQ (weaker replay/partition ordering), direct REST only (no buffering under bursts).
