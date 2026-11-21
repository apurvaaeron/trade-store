# ADR-0004: Clock-driven daily expiry with start-up catch-up

**Status:** Accepted

## Context
"When a trade's maturity date is surpassed, the store should automatically mark the trade as expired." Expiry
depends on the business date, which must be deterministic in tests and correct across time zones.

## Decision
- A single `Clock` bean (zone `tradestore.zone`) is the only source of "today" for validation and expiry.
- `TradeExpiryScheduler` runs a set-based `UPDATE … WHERE maturity_date < :today AND expired = false` daily at 00:00
  in the business zone, and once at start-up so a missed run is caught up.
- A partial index on `maturity_date WHERE expired = false` keeps the sweep cheap as data grows.
- `POST /api/v1/trades/expiry-run` triggers it on demand for operations.

## Consequences
- Idempotent and safe to run on several replicas (the `UPDATE` is atomic; repeated runs change nothing).
- For multiple replicas in production, add ShedLock so only one replica runs the job each night.
