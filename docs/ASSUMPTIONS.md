# Assumptions and interpretation of the brief

The brief gives three rules and a four-row sample. Fine-grained behaviour is not specified, so these are the
assumptions the implementation and tests are built on. Each one is covered by at least one automated test.

## Reading the sample data

| Row | What the rules say | How the store behaves |
|---|---|---|
| T1 v1, maturity 20/05/2020 | Valid only while today ≤ 20/05/2020 | Accepted on 14/05/2020 (acceptance test); rejected if received after maturity |
| T2 v2, maturity 20/05/2021 | Valid | Accepted |
| T2 v1 (arrives after v2) | Lower version | Rejected with `LowerVersionException`; v2 stays |
| T3 v3, maturity 20/05/2014, Expired = Y | Contradiction: a past maturity is rejected on receipt, yet the row is stored and expired | Resolved as two cases: rejected if it **arrives** after maturity; if it was stored **before** maturity it is marked `Expired = Y` once maturity passes |

## Business rules

| # | Assumption |
|---|---|
| A1 | A trade is identified by `tradeId`. The store keeps the **latest version** of each trade (current state). |
| A2 | Lower version → rejected with `LowerVersionException`. Same version → replaces the record (supports corrections and re-sends). Higher version → replaces. |
| A3 | Maturity date **equal to today is accepted**; only a date strictly before today is rejected (`MaturityDateInPastException`). |
| A4 | Maturity is checked **before** version: a same/higher version with a past maturity is rejected and the stored record is untouched. |
| A5 | "Today" is the business date in a configured time zone (`tradestore.zone`, default UTC), read from an injected `Clock`. |
| A6 | `createdDate` is optional on input; when absent the store stamps today's business date. When present it is kept as sent. |
| A7 | `expired` is not accepted from the sender; the store owns it. New/replaced trades are stored with `expired = N`. |
| A8 | Expiry runs daily at 00:00 in the business zone **and once at start-up** (catches up after downtime). It is idempotent. |
| A9 | An expired trade can be revived by a newer version with a future maturity (it passes every rule); it is stored with `expired = N`. |
| A10 | `version` is a positive integer. `tradeId`, `counterPartyId`, `bookId`, `maturityDate` are mandatory. Counterparty and book are not validated against reference data (out of scope). |
| A11 | Dates are exchanged as `dd/MM/yyyy` as in the brief, and stored as SQL `DATE`. |

## Transmission and delivery

| # | Assumption |
|---|---|
| A12 | "Any method of transmission": Kafka (`trades.inbound`) is the primary channel; REST is offered synchronously (`POST /api/v1/trades`) and asynchronously via Kafka (`POST /api/v1/trades/async`). Every channel goes through the same `TradeIngestionService`. |
| A13 | Messages are keyed by `tradeId`, so all versions of one trade are consumed in order from one partition. Different trades are processed in parallel. |
| A14 | Delivery is at-least-once. A same-version redelivery can overwrite a later correction at that version because equal versions replace the stored record: the message has no event id or source timestamp to tell a replay from a correction (`TradeStoreFlowIT.replayOfAnEqualVersionRestoresTheOlderPayload`). Accepted as a case-study limitation; in production, if same-version corrections matter, add a source sequence number or event id and reject stale replays. |
| A15 | Trades can still race (e.g. REST and Kafka at once, or partition rebalancing). The database upsert only applies when `stored.version <= incoming.version`, so a lower version can never overwrite a higher one. |
| A16 | Decodable business rejections are published to `trades.rejected` and audited on a best-effort basis; REST business-rule rejections return HTTP 422 and decoded payload validation failures return 400. REST payloads that cannot be deserialized return 400 before ingestion and are not audited. |
| A16a | `POST /api/v1/trades/async` waits up to `tradestore.async.publish-timeout` (default 5 s) for Kafka to confirm the write (`acks=all`). `202 Accepted` means Kafka confirmed it, not that the trade was stored. Without confirmation it returns `503` with `outcome` `UNKNOWN` (no answer in time: the trade may still have been queued) or `FAILED` (Kafka reported an error), plus `Retry-After`. Resending the identical payload is safe because an equal version replaces the stored trade with the same data; an event id / idempotency key is the production follow-up. |
| A17 | Technical failures, including Kafka messages that cannot be deserialized, are retried 3 times, then parked on `trades.inbound.dlt`; undecodable Kafka messages bypass the audit. |

## Storage

| # | Assumption |
|---|---|
| A18 | PostgreSQL (SQL) holds the current state of each trade and is the source of truth. |
| A19 | MongoDB (NoSQL) holds best-effort immutable audit events for decoded `TradeRequest` submissions that reach ingestion (accepted and business-rejected, with reason and decoded payload). REST payloads that cannot be deserialized and undecodable Kafka records produce no audit event. |
| A20 | An audit write failure is logged and counted in `tradestore.audit.failures{outcome}` for alerting, but does not reject or roll back an accepted trade (see ADR-0002; the outbox pattern is the production follow-up). |

## Out of scope

Authentication/authorisation, multi-region deployment, reference-data validation, trade history queries beyond the
audit log, and UI. NFR targets for these are listed in [NFR.md](NFR.md) as design intent.
