# Run Trade Store locally

This guide runs a local development/test stack; it does not deploy or host the application. Use the feature branch below until it has been merged—do not switch to `main` for these instructions.

## 1. Check out the feature branch

```bash
git clone https://github.com/apurvaaeron/trade-store.git
cd trade-store
git fetch origin
git switch --track origin/devin/1795222949-trade-store-impl
```

## 2. Prerequisites

- Docker Engine with Docker Compose v2. The app is built and run in containers, so host Java and Maven are **not** required to start the stack.
- As a practical estimate, allow about 4 GB of RAM for Docker (6–8 GB is more comfortable) and several GB of free disk for the first image/dependency downloads. These are guidelines, not enforced limits.
- Optional, for running tests on the host: JDK 17 and Maven 3.9.x. This repository has no Maven wrapper. `mvn verify` also needs Docker because it runs Testcontainers tests.

The commands below use Bash syntax (Linux, macOS, WSL, or Git Bash). In Windows PowerShell, use `curl.exe` rather than `curl` (which may be an alias), enter each request as one line, and repeat the GET command in the polling step manually; alternatively use WSL or Git Bash.

## 3. Start and check the stack

From the repository root:

```bash
docker compose up -d --build --wait --wait-timeout 240
docker compose ps
curl -i http://127.0.0.1:8080/actuator/health
```

Compose waits for PostgreSQL, MongoDB, Kafka, and the app to become healthy. The published ports are bound to loopback: app `8080`, PostgreSQL `5432`, MongoDB `27017`, and Kafka `9092`; the stack is intended for local testing, not LAN exposure.

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>
- Prometheus metrics: <http://localhost:8080/actuator/prometheus>

## 4. Try the REST API

Dates use `dd/MM/yyyy`. Use `31/12/2099` for a reliably future maturity date and `20/05/2014` for a past date, without needing to calculate today's date. `createdDate` is optional and defaults to today's business date. IDs must be 1–64 characters using only letters, digits, `.`, `_`, or `-`.

### New, same, higher, and lower versions

Each successful synchronous request returns **201 Created** and the stored trade. The same version replaces the record; a higher version replaces it too. A lower version returns **422** and must leave the higher version stored.

Same-version replacement is not guaranteed idempotent: replaying an older payload at the same version can overwrite a later correction at that version.

```bash
# New trade: 201, version 1
curl -i -X POST http://127.0.0.1:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeId":"LOCAL-101","version":1,"counterPartyId":"CP-ORIGINAL","bookId":"BOOK-1","maturityDate":"31/12/2099"}'

# Same version: 201; this replaces counterPartyId with CP-SAME
curl -i -X POST http://127.0.0.1:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeId":"LOCAL-101","version":1,"counterPartyId":"CP-SAME","bookId":"BOOK-1","maturityDate":"31/12/2099"}'

# Higher version: 201, version 2
curl -i -X POST http://127.0.0.1:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeId":"LOCAL-101","version":2,"counterPartyId":"CP-HIGHER","bookId":"BOOK-1","maturityDate":"31/12/2099"}'

# Lower version: 422 with reason LOWER_VERSION; version 2 remains stored
curl -i -X POST http://127.0.0.1:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeId":"LOCAL-101","version":1,"counterPartyId":"CP-LOWER","bookId":"BOOK-1","maturityDate":"31/12/2099"}'

# Confirm the current state (version 2, CP-HIGHER)
curl -i http://127.0.0.1:8080/api/v1/trades/LOCAL-101
```

After this sequence, `LOCAL-101` remains at version 2. Re-running the version-1 steps returns **422**; use a new trade ID or clear the local Compose data first (see [Troubleshooting and cleanup](#troubleshooting-and-cleanup)).

### Invalid payloads and past maturity

Invalid JSON field types and impossible calendar dates return **400 Bad Request**. A well-formed trade with a maturity date before today returns **422** with reason `MATURITY_DATE_IN_PAST`.

```bash
# Fractional version: 400
curl -i -X POST http://127.0.0.1:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeId":"LOCAL-FRACTION","version":2.9,"counterPartyId":"CP-1","bookId":"BOOK-1","maturityDate":"31/12/2099"}'

# Invalid calendar date: 400
curl -i -X POST http://127.0.0.1:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeId":"LOCAL-INVALID-DATE","version":1,"counterPartyId":"CP-1","bookId":"BOOK-1","maturityDate":"31/02/2030"}'

# Past maturity: 422 with reason MATURITY_DATE_IN_PAST
curl -i -X POST http://127.0.0.1:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeId":"LOCAL-PAST","version":1,"counterPartyId":"CP-1","bookId":"BOOK-1","maturityDate":"20/05/2014"}'
```

### Asynchronous Kafka ingestion and audit

The asynchronous endpoint publishes to Kafka topic `trades.inbound` and returns **202 Accepted** with `status: QUEUED`. The consumer stores the trade after that response, so poll the GET endpoint for eventual **200 OK**:

```bash
curl -i -X POST http://127.0.0.1:8080/api/v1/trades/async \
  -H 'Content-Type: application/json' \
  -d '{"tradeId":"LOCAL-ASYNC","version":1,"counterPartyId":"CP-KAFKA","bookId":"BOOK-1","maturityDate":"31/12/2099"}'

# Bash: check for up to 20 seconds while the Kafka consumer processes the message
for attempt in $(seq 1 20); do
  status=$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/v1/trades/LOCAL-ASYNC || true)
  if [ "$status" = "200" ]; then
    curl -sS http://127.0.0.1:8080/api/v1/trades/LOCAL-ASYNC
    break
  fi
  if [ "$attempt" = "20" ]; then echo "Trade not visible yet; inspect app logs." >&2; fi
  sleep 1
done

# Audit history for the synchronous examples (MongoDB; best effort)
curl -i http://127.0.0.1:8080/api/v1/trades/LOCAL-101/audit
```

The audit contains decoded submissions that reach ingestion, including accepted trades and business rejections, when the best-effort MongoDB write succeeds. REST payloads that fail decoding return 400 before ingestion and are not audited. Undecodable Kafka records go to `trades.inbound.dlt` and are not audited; business rejections are published to `trades.rejected`.

### Expiry

```bash
curl -i -X POST http://127.0.0.1:8080/api/v1/trades/expiry-run
```

This runs the normal expiry sweep and returns `{"expired":N}`. It marks stored trades whose maturity date is strictly before today; a trade maturing today remains active. It does not advance time or force future trades to expire. The trades created above use a future date, so normally the count is zero. The API rejects past-maturity trades, so testing date progression/expiry is best done by the automated tests, which use a controllable `Clock`.

## 5. Run tests (optional)

With JDK 17 and Maven installed:

```bash
mvn test
mvn verify
mvn test-compile org.pitest:pitest-maven:mutationCoverage
```

`mvn test` runs unit/web tests. `mvn verify` also runs Testcontainers and acceptance tests, so Docker must be running. Reports are under `target/surefire-reports/`, `target/failsafe-reports/`, `target/site/jacoco/`, and `target/pit-reports/`.

## Troubleshooting and cleanup

- **Port already in use:** check for another process using `8080`, `5432`, `27017`, or `9092`, stop it, then retry. Compose binds these ports to `127.0.0.1`; do not change them to `0.0.0.0`. Kafka also advertises `localhost:9092`, so changing its host port requires updating that listener consistently.
- **Build reports Maven Central HTTP 429:** the Docker build runs Maven inside the builder image and does not inherit the host's `~/.m2/settings.xml`. Retry after the rate limit clears, or configure a Maven mirror for the container build; do not copy credentials into the repository.
- **Service/app startup or request problems:** inspect `docker compose ps` and `docker compose logs --tail=100 app` (or `docker compose logs -f app` while reproducing).
- **Remove the local stack:**

  ```bash
  docker compose down
  ```

  This Compose file defines no named data volumes. `docker compose down` removes the containers and their local PostgreSQL, MongoDB, and Kafka data. Use `docker compose stop` instead if you only want to stop the stack and keep those containers/data for a later `docker compose start`.
