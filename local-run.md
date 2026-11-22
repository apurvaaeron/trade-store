# Run Trade Store locally

This guide runs a local development/test stack; it does not deploy or host the application. Use the feature branch below until it has been merged—do not switch to `main` for these instructions.

## 1. Get the code

The same commands work in PowerShell and Bash:

```bash
git clone https://github.com/apurvaaeron/trade-store.git
cd trade-store
```

## 2. Prerequisites

- Docker Engine with Docker Compose v2. The app is built and run in containers, so host Java and Maven are **not** required to start the stack.
- As a practical estimate, allow about 4 GB of RAM for Docker (6–8 GB is more comfortable) and several GB of free disk for the first image/dependency downloads. These are guidelines, not enforced limits.
- Optional, for running tests on the host: JDK 17 or newer (a JDK, not a JRE: `javac -version` must work) and Maven 3.9.x. This repository has no Maven wrapper. `mvn verify` also needs Docker because it runs Testcontainers tests.

### Which shell

Each step shows **Windows PowerShell** commands first (they work in Windows PowerShell 5.1 and PowerShell 7). The equivalent **Bash** commands for macOS, Linux, WSL or Git Bash are under a collapsible "macOS / Linux / Git Bash" heading.

PowerShell notes:
- Run everything from the repository folder (`cd trade-store`).
- Don't use `curl` on its own in PowerShell: it is a shortcut for `Invoke-WebRequest` and prompts for a `Uri`. The steps use `Invoke-RestMethod` / `Invoke-WebRequest`, or `curl.exe` where the real curl is wanted.
- Quote Maven `-D` options that contain a dot, e.g. `mvn "-Dit.test=TradeStoreFlowIT"`; otherwise PowerShell splits them.
- Docker Desktop on Windows does not enable `--network host` by default, so the Postman and load-test containers below join the app container's network instead (`--network "container:$(docker compose ps -q app)"`). This needs the Compose stack running.

## 3. Start and check the stack

From the repository root:

```powershell
docker compose up -d --build --wait --wait-timeout 240
docker compose ps
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

Expected: `status` is `UP`.

<details>
<summary>macOS / Linux / Git Bash</summary>

```bash
docker compose up -d --build --wait --wait-timeout 240
docker compose ps
curl -i http://127.0.0.1:8080/actuator/health
```

</details>

Compose waits for PostgreSQL, MongoDB, Kafka, and the app to become healthy. The published ports are bound to loopback: app `8080`, PostgreSQL `5432`, MongoDB `27017`, and Kafka `9092`; the stack is intended for local testing, not LAN exposure.

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>
- Prometheus metrics: <http://localhost:8080/actuator/prometheus>

## 4. Try the REST API

Dates use `dd/MM/yyyy`. Use `31/12/2099` for a reliably future maturity date and `20/05/2014` for a past date, without needing to calculate today's date. `createdDate` is optional and defaults to today's business date. IDs must be 1–64 characters using only letters, digits, `.`, `_`, or `-`.

### PowerShell helper (run once per PowerShell window)

`Invoke-RestMethod` throws on 4xx responses and hides the body, so paste this helper first. It prints the HTTP status and the response body for any request:

```powershell
$Base = "http://127.0.0.1:8080"
function Invoke-Api([string]$Method, [string]$Path, [string]$Body) {
    $request = @{ Method = $Method; Uri = "$Base$Path"; UseBasicParsing = $true }
    if ($Body) { $request.ContentType = "application/json"; $request.Body = $Body }
    try {
        $response = Invoke-WebRequest @request
        "HTTP $($response.StatusCode)"; $response.Content
    } catch {
        if (-not $_.Exception.Response) { throw }
        "HTTP $([int]$_.Exception.Response.StatusCode)"; $_.ErrorDetails.Message
    }
}
```

### New, same, higher, and lower versions

Each successful synchronous request returns **201 Created** and the stored trade. The same version replaces the record; a higher version replaces it too. A lower version returns **422** and must leave the higher version stored.

Same-version replacement is not guaranteed idempotent: replaying an older payload at the same version can overwrite a later correction at that version.

```powershell
# New trade: 201, version 1
Invoke-Api POST /api/v1/trades '{"tradeId":"LOCAL-101","version":1,"counterPartyId":"CP-ORIGINAL","bookId":"BOOK-1","maturityDate":"31/12/2099"}'

# Same version: 201; this replaces counterPartyId with CP-SAME
Invoke-Api POST /api/v1/trades '{"tradeId":"LOCAL-101","version":1,"counterPartyId":"CP-SAME","bookId":"BOOK-1","maturityDate":"31/12/2099"}'

# Higher version: 201, version 2
Invoke-Api POST /api/v1/trades '{"tradeId":"LOCAL-101","version":2,"counterPartyId":"CP-HIGHER","bookId":"BOOK-1","maturityDate":"31/12/2099"}'

# Lower version: 422 with reason LOWER_VERSION; version 2 remains stored
Invoke-Api POST /api/v1/trades '{"tradeId":"LOCAL-101","version":1,"counterPartyId":"CP-LOWER","bookId":"BOOK-1","maturityDate":"31/12/2099"}'

# Confirm the current state (version 2, CP-HIGHER)
Invoke-Api GET /api/v1/trades/LOCAL-101
```

<details>
<summary>macOS / Linux / Git Bash</summary>

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

</details>

After this sequence, `LOCAL-101` remains at version 2. Re-running the version-1 steps returns **422**; use a new trade ID or clear the local Compose data first (see [Troubleshooting and cleanup](#troubleshooting-and-cleanup)).

### Invalid payloads and past maturity

Invalid JSON field types and impossible calendar dates return **400 Bad Request**. A well-formed trade with a maturity date before today returns **422** with reason `MATURITY_DATE_IN_PAST`.

```powershell
# Fractional version: 400
Invoke-Api POST /api/v1/trades '{"tradeId":"LOCAL-FRACTION","version":2.9,"counterPartyId":"CP-1","bookId":"BOOK-1","maturityDate":"31/12/2099"}'

# Invalid calendar date: 400
Invoke-Api POST /api/v1/trades '{"tradeId":"LOCAL-INVALID-DATE","version":1,"counterPartyId":"CP-1","bookId":"BOOK-1","maturityDate":"31/02/2030"}'

# Past maturity: 422 with reason MATURITY_DATE_IN_PAST
Invoke-Api POST /api/v1/trades '{"tradeId":"LOCAL-PAST","version":1,"counterPartyId":"CP-1","bookId":"BOOK-1","maturityDate":"20/05/2014"}'
```

<details>
<summary>macOS / Linux / Git Bash</summary>

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

</details>

### Asynchronous Kafka ingestion and audit

The asynchronous endpoint publishes to Kafka topic `trades.inbound` and returns **202 Accepted** with `status: QUEUED`. `202` means Kafka confirmed the write, not that the trade is stored yet. If Kafka doesn't confirm within 5 s (`TRADESTORE_ASYNC_PUBLISH_TIMEOUT`), the endpoint returns **503** with `outcome` `UNKNOWN` (it may still have been queued) or `FAILED`. Resending the same payload is fine unless you've sent another change to that trade version since (see assumption A14). The consumer stores the trade after the 202, so poll the GET endpoint for eventual **200 OK**:

```powershell
Invoke-Api POST /api/v1/trades/async '{"tradeId":"LOCAL-ASYNC","version":1,"counterPartyId":"CP-KAFKA","bookId":"BOOK-1","maturityDate":"31/12/2099"}'

# Check for up to 20 seconds while the Kafka consumer processes the message
foreach ($attempt in 1..20) {
    $result = Invoke-Api GET /api/v1/trades/LOCAL-ASYNC
    if ($result[0] -eq "HTTP 200") { $result; break }
    if ($attempt -eq 20) { Write-Warning "Trade not visible yet; inspect app logs." }
    Start-Sleep -Seconds 1
}

# Audit history for the synchronous examples (MongoDB; best effort)
Invoke-Api GET /api/v1/trades/LOCAL-101/audit
```

<details>
<summary>macOS / Linux / Git Bash</summary>

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

</details>

The audit contains decoded submissions that reach ingestion, including accepted trades and business rejections, when the best-effort MongoDB write succeeds. REST payloads that fail decoding return 400 before ingestion and are not audited. Undecodable Kafka records go to `trades.inbound.dlt` and are not audited; business rejections are published to `trades.rejected`.

### Expiry

```powershell
Invoke-Api POST /api/v1/trades/expiry-run
```

<details>
<summary>macOS / Linux / Git Bash</summary>

```bash
curl -i -X POST http://127.0.0.1:8080/api/v1/trades/expiry-run
```

</details>

This runs the normal expiry sweep and returns `{"expired":N}`. It marks stored trades whose maturity date is strictly before today; a trade maturing today remains active. It does not advance time or force future trades to expire. The trades created above use a future date, so normally the count is zero. The API rejects past-maturity trades, so testing date progression/expiry is best done by the automated tests, which use a controllable `Clock`.

## 5. Run the Postman collection (optional)

The [`postman/`](postman/) folder has an 18-request collection with 37 assertions covering every rule above, plus Kafka, audit, expiry and metrics.

- **Postman app:** File > Import both `postman/trade-store.postman_collection.json` and `postman/local.postman_environment.json`, select the **Trade Store - local** environment, then run the collection in order with the Collection Runner.
- **Command line (Newman in Docker, no Node install needed).** The container shares the app container's network, so `http://localhost:8080` reaches the app on Windows, macOS and Linux:

```powershell
docker run --rm --network "container:$(docker compose ps -q app)" -v "${PWD}/postman:/etc/newman" postman/newman:6-alpine run trade-store.postman_collection.json -e local.postman_environment.json
```

<details>
<summary>macOS / Linux / Git Bash</summary>

```bash
docker run --rm --network "container:$(docker compose ps -q app)" -v "$PWD/postman:/etc/newman" postman/newman:6-alpine \
  run trade-store.postman_collection.json -e local.postman_environment.json
```

</details>

Pass: the summary table shows `0` in the `failed` column. With Node.js installed, `npm install -g newman` then `newman run postman/trade-store.postman_collection.json -e postman/local.postman_environment.json` also works.

The first request generates unique trade IDs, so the collection can be re-run without cleaning data. Run the requests in order; later requests depend on earlier ones.

## 6. Run JUnit and integration tests with reports

Needs JDK 17, Maven 3.9.x and Docker running (integration tests start their own PostgreSQL, MongoDB and Kafka with Testcontainers; the Compose stack does not need to be up).

| What | Command | Tests | HTML report |
|---|---|---|---|
| Unit + web-slice tests (JUnit, Mockito, MockMvc; no Docker) | `mvn test` | 57 | `target/reports/surefire.html` (after the report step below) |
| Everything: unit, integration, Cucumber acceptance, concurrency, performance smoke, coverage gate | `mvn verify` | 57 + 31 | `target/reports/surefire.html`, `target/reports/failsafe.html`, `target/cucumber-report.html`, `target/site/jacoco/index.html` |
| Mutation testing (PIT, gate ≥ 70%) | `mvn test-compile org.pitest:pitest-maven:mutationCoverage` | – | `target/pit-reports/index.html` |

```powershell
mvn verify                      # about 2-4 minutes; BUILD SUCCESS means all tests and the 85% coverage gate passed
mvn surefire-report:report-only surefire-report:failsafe-report-only "-DlinkXRef=false"
Invoke-Item target\reports\surefire.html, target\reports\failsafe.html, target\cucumber-report.html, target\site\jacoco\index.html
```

The second command turns the raw XML results into HTML pages without re-running the tests; the third opens them in your browser.

<details>
<summary>macOS / Linux / Git Bash</summary>

```bash
mvn verify
mvn surefire-report:report-only surefire-report:failsafe-report-only -DlinkXRef=false
open target/reports/surefire.html target/reports/failsafe.html target/cucumber-report.html target/site/jacoco/index.html   # macOS
xdg-open target/reports/failsafe.html                                                                                       # Linux
```

</details>

- **surefire.html:** unit and web-slice tests per class, with pass/fail and timings.
- **failsafe.html:** integration tests (`*IT`): repository upsert, Kafka flow, dead-letter handling, failed rejection publish, same-version replay, REST/Kafka race, audit, concurrency, performance smoke, Cucumber.
- **cucumber-report.html:** the assignment's sample trades as business-readable scenarios.
- **jacoco/index.html:** line/branch coverage of unit and integration tests combined (about 97% lines).
- Raw XML/text output stays in `target/surefire-reports/` and `target/failsafe-reports/`.

Run a single class while presenting:

```powershell
mvn test "-Dtest=TradeStoreServiceTest"
mvn verify "-Dtest=none" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dit.test=TradeStoreFlowIT" "-Djacoco.skip=true"
```

The quotes are required in PowerShell and harmless in Bash.

## 7. Demonstrate the NFRs

Targets come from [docs/NFR.md](docs/NFR.md). Start the stack first (section 3). Load tests use [k6](https://k6.io) through Docker, so nothing extra needs installing; each run prints a summary and shows a check mark (pass) or cross (fail) for each threshold. Like Newman, the k6 container joins the app container's network.

Measured on an 8-vCPU Linux VM (your laptop numbers will differ):

| NFR | Target | How to show it | Result on the VM |
|---|---|---|---|
| Throughput | ≥ 1,000 trades/s | Kafka (async) load test, step 1 | 1,000 req/s for 30 s, 0 errors, all 30,001 trades consumed |
| Latency | p99 < 200 ms (REST sync) | Sync load test, step 2 | p99 13 ms at 200 req/s |
| Durability (RPO 0) | No accepted trade lost | Stop the app, publish, restart, step 3 | 1,000 of 1,000 stored, consumer lag 0 |
| Resilience | Poison message never blocks a partition | Step 3 poison message → DLT | Message on `trades.inbound.dlt`; later trades still processed |
| Ordering / consistency | Lower version never overwrites | Concurrency test, step 4 (plus Postman) | 40 shuffled versions on 8 threads; highest wins |
| Availability | Liveness/readiness probes | Step 5 | Both `UP` |
| Observability | Accepted/rejected counts | Step 5 | Prometheus counters by channel, outcome, reason |
| Security | Build fails on critical CVEs; non-root container | Step 6 | 0 critical findings; app runs as `app` |
| Maintainability | Coverage ≥ 85%, mutation ≥ 70% | Section 6 | 97% line coverage; PIT 83% |

**1. Throughput through Kafka (1,000 trades/s for 30 s)**

```powershell
docker run --rm --network "container:$(docker compose ps -q app)" -v "${PWD}/perf:/perf" grafana/k6:0.54.0 run -e MODE=async -e RATE=1000 -e DURATION=30s /perf/nfr-load.js
(Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/actuator/prometheus).Content -split "`n" | Select-String '^tradestore_trades_received_total'
```

<details>
<summary>macOS / Linux / Git Bash</summary>

```bash
docker run --rm --network "container:$(docker compose ps -q app)" -v "$PWD/perf:/perf" grafana/k6:0.54.0 run -e MODE=async -e RATE=1000 -e DURATION=30s /perf/nfr-load.js
curl -s http://127.0.0.1:8080/actuator/prometheus | grep '^tradestore_trades_received_total'
```

</details>

Pass: `http_reqs` about 1000/s, `dropped_iterations` 0, and after a few seconds the `channel="KAFKA",outcome="accepted"` counter has grown by the same number of requests (every queued trade was consumed and stored). The in-build equivalent is `PerformanceSmokeIT`, which pushes 5,000 trades through Kafka and prints the rate (about 1,100 trades/s on the VM) to `target/failsafe-reports/com.tradestore.it.PerformanceSmokeIT-output.txt`.

**2. Latency on the synchronous REST path (p99 < 200 ms)**

```powershell
docker run --rm --network "container:$(docker compose ps -q app)" -v "${PWD}/perf:/perf" grafana/k6:0.54.0 run -e RATE=200 -e DURATION=60s /perf/nfr-load.js
```

<details>
<summary>macOS / Linux / Git Bash</summary>

```bash
docker run --rm --network "container:$(docker compose ps -q app)" -v "$PWD/perf:/perf" grafana/k6:0.54.0 run -e RATE=200 -e DURATION=60s /perf/nfr-load.js
```

</details>

Pass: `http_req_duration` p(99) under 200 ms and `http_req_failed` 0%. Raise `RATE` to find your machine's limit; k6 exits non-zero when a threshold fails (check with `$LASTEXITCODE` in PowerShell, `$?` in Bash).

**3. Durability and dead-letter handling**

Stop the app, publish 1,000 trades plus one malformed message straight to Kafka, then restart the app:

```powershell
docker compose stop app
$lines = 1..1000 | ForEach-Object { "DUR-$_|" + '{"tradeId":"DUR-' + $_ + '","version":1,"counterPartyId":"CP-1","bookId":"B1","maturityDate":"31/12/2099"}' }
$lines + 'POISON-1|this is not json' | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic trades.inbound --property parse.key=true --property "key.separator=|"
docker compose start app
Start-Sleep -Seconds 30

docker compose exec -T postgres psql -U tradestore -tAc "select count(*) from trades where trade_id like 'DUR-%'"
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic trades.inbound.dlt --from-beginning --max-messages 1 --timeout-ms 15000 --property print.key=true
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:29092 --describe --group trade-store
```

<details>
<summary>macOS / Linux / Git Bash</summary>

```bash
docker compose stop app
{ seq 1 1000 | awk '{printf "DUR-%d|{\"tradeId\":\"DUR-%d\",\"version\":1,\"counterPartyId\":\"CP-1\",\"bookId\":\"B1\",\"maturityDate\":\"31/12/2099\"}\n",$1,$1}'
  echo 'POISON-1|this is not json'; } |
  docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 \
    --topic trades.inbound --property parse.key=true --property key.separator='|'
docker compose start app && sleep 30

docker compose exec -T postgres psql -U tradestore -tAc "select count(*) from trades where trade_id like 'DUR-%'"
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 \
  --topic trades.inbound.dlt --from-beginning --max-messages 1 --timeout-ms 15000 --property print.key=true
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:29092 --describe --group trade-store
```

</details>

Pass: count is `1000`, the DLT consumer prints `POISON-1  this is not json`, and `LAG` is `0` on every partition. Messages wait in Kafka while the app is down and are stored after restart; the malformed one is sent to the dead-letter topic and does not block the trades behind it. Re-running replaces the same DUR IDs (same version), so the count stays 1000; change the `DUR-` prefix in the producer and count commands for a fresh count.

**4. Ordering and concurrency**

```powershell
mvn verify "-Dtest=none" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dit.test=ConcurrentVersionsIT,PerformanceSmokeIT" "-Djacoco.skip=true"
```

The same command works in Bash.

`ConcurrentVersionsIT` fires 40 shuffled versions of the same trade from 8 threads and asserts the highest version is stored. Sections 4 and 5 show the same lower-version rejection over the API.

**5. Availability probes and observability**

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health/liveness
Invoke-RestMethod http://127.0.0.1:8080/actuator/health/readiness
(Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/actuator/prometheus).Content -split "`n" | Select-String '^tradestore_|^http_server_requests_seconds_count'
```

<details>
<summary>macOS / Linux / Git Bash</summary>

```bash
curl -s http://127.0.0.1:8080/actuator/health/liveness
curl -s http://127.0.0.1:8080/actuator/health/readiness
curl -s http://127.0.0.1:8080/actuator/prometheus | grep -E '^tradestore_|^http_server_requests_seconds_count'
```

</details>

The trade counters are labelled by `channel` (REST/KAFKA), `outcome` and rejection `reason`; `tradestore_trades_expired_total` counts expiries.

**6. Security gates**

```powershell
mvn -B package -DskipTests "-Djacoco.skip=true"
docker run --rm -v "${PWD}/target:/scan" aquasec/trivy:0.57.1 rootfs --scanners vuln --severity HIGH,CRITICAL --exit-code 1 /scan; "exit $LASTEXITCODE"
docker compose exec app id
```

<details>
<summary>macOS / Linux / Git Bash</summary>

```bash
mvn -B package -DskipTests -Djacoco.skip=true
docker run --rm -v "$PWD/target:/scan" aquasec/trivy:0.57.1 rootfs --scanners vuln --severity HIGH,CRITICAL --exit-code 1 /scan; echo "exit $?"
docker compose exec app id
```

</details>

Pass: Trivy scans every library inside the built JAR, lists no HIGH or CRITICAL vulnerabilities and prints `exit 0` (it exits 1 on any, which is what fails the CI build). `id` shows the container runs as the non-root `app` user. To show the gate failing, CI's `oss-vulnerability-scan` job runs the same scan on every push. OWASP Dependency-Check is optional; it runs with `$env:NVD_API_KEY = "<your key>"; mvn -Powasp verify -DskipTests` in PowerShell (`NVD_API_KEY=<your key> mvn -Powasp verify -DskipTests` in Bash) (a free NVD API key is needed; without one the first run takes very long).

## Troubleshooting and cleanup

- **PowerShell asks `Supply values for the following parameters: Uri`:** you ran `curl` (an alias for `Invoke-WebRequest`) with Bash-style options. Press Ctrl+C and use the PowerShell commands above, or `curl.exe`.
- **`mvn` fails with `No compiler is provided in this environment`:** Maven is running on a JRE, not a JDK. Install JDK 17+ and point `JAVA_HOME` at it, then open a new PowerShell window:

  ```powershell
  winget install EclipseAdoptium.Temurin.17.JDK
  $jdk = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1).FullName
  [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdk, "User")
  $env:JAVA_HOME = $jdk; $env:Path = "$jdk\bin;$env:Path"
  javac -version   # must print javac 17.x
  mvn -v           # runtime path must be under the JDK folder
  ```

  If `mvn -v` still shows the old Java, remove the JRE entry from the system `JAVA_HOME`/`Path` variables.
- **Port already in use:** check for another process using `8080`, `5432`, `27017`, or `9092` (PowerShell: `Get-NetTCPConnection -LocalPort 8080`), stop it, then retry. Compose binds these ports to `127.0.0.1`; do not change them to `0.0.0.0`. Kafka also advertises `localhost:9092`, so changing its host port requires updating that listener consistently.
- **Build reports Maven Central HTTP 429:** the Docker build runs Maven inside the builder image and does not inherit the host's `~/.m2/settings.xml`. Retry after the rate limit clears, or configure a Maven mirror for the container build; do not copy credentials into the repository.
- **Service/app startup or request problems:** inspect `docker compose ps` and `docker compose logs --tail=100 app` (or `docker compose logs -f app` while reproducing).
- **Remove the local stack:**

  ```powershell
  docker compose down
  ```

  This Compose file defines no named data volumes. `docker compose down` removes the containers and their local PostgreSQL, MongoDB, and Kafka data. Use `docker compose stop` instead if you only want to stop the stack and keep those containers/data for a later `docker compose start`.
