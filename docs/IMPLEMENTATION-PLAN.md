# Build prompt: Trade Store case study (for a local coding agent in IntelliJ)

> How to use: paste everything below the line into your IntelliJ coding agent (one session, empty repo
> opened as the project). The agent should work through the phases in order and stop at each checkpoint
> until the listed command passes.

---

## 0. Role, goal and working rules

You are a senior Java engineer building a production-quality case-study solution from an empty repository.
I have already researched the brief and made the design decisions below. **Implement exactly this design; do
not substitute technologies, rename public contracts or change business rules.** If something here is
ambiguous or impossible, stop and ask me instead of guessing.

Working rules:
- Work test-first where practical: for each behaviour, write the failing test, then the code, then refactor.
- Small, single-purpose classes; constructor injection; `final` fields; Java records for DTOs and config;
  **no Lombok**; no field injection; no setters on the entity.
- Short comments only where the code's *why* is not obvious. No commented-out code.
- After each phase, run the phase's checkpoint command and fix failures before moving on.
- Never commit secrets. Never weaken a quality gate to make the build pass.
- The developer machine is **Windows + PowerShell** (also support macOS/Linux). JDK 17+ (a JDK, not a JRE),
  Maven 3.9.x, Docker Desktop.

## 1. The brief (verbatim summary)

Build a Spring Boot application with a streaming tool and both SQL and NoSQL databases. Cover all functionality
with JUnit tests (TDD). Provide a GitHub Actions pipeline with automated regression tests and an OSS
vulnerability scan that **fails the build on critical or blocker vulnerabilities**. Use PlantUML for sequence,
class and design diagrams. Disclose all AI prompts in a separate document.

Problem: thousands of trades arrive at one store through any method of transmission. Store each trade in sequence.

Sample data (fields: Trade Id, Version, Counter-Party Id, Book-Id, Maturity Date, Created Date, Expired):

| Trade Id | Version | Counter-Party Id | Book-Id | Maturity Date | Created Date | Expired |
|---|---|---|---|---|---|---|
| T1 | 1 | CP-1 | B1 | 20/05/2020 | today | N |
| T2 | 2 | CP-2 | B1 | 20/05/2021 | today | N |
| T2 | 1 | CP-1 | B1 | 20/05/2021 | 14/03/2015 | N |
| T3 | 3 | CP-3 | B2 | 20/05/2014 | today | Y |

Rules:
1. A lower version is rejected with an exception. The same version replaces the current record.
2. A trade with a maturity date earlier than today is rejected.
3. When a trade's maturity date passes, the store automatically marks it expired.

## 2. Design decisions (already made; implement as stated)

| Topic | Decision |
|---|---|
| Language / build | Java 17 (`<java.version>17</java.version>`), Maven, Spring Boot **4.2.0** parent |
| Streaming | Apache Kafka (Spring for Apache Kafka) is the primary channel; REST is a second channel |
| SQL | PostgreSQL 16 holds the **current state** of each trade and is the source of truth |
| NoSQL | MongoDB 7 holds a **best-effort audit trail** of decoded submissions |
| Schema | Flyway migrations; Hibernate `ddl-auto: validate` only |
| Version rule | Enforced atomically in the database with a conditional upsert (safe under concurrent consumers) |
| Ordering | Kafka messages keyed by `tradeId` (per-trade ordering; no global ordering) |
| "Today" | Business date from an injected `java.time.Clock` in a configurable zone (default UTC) |
| Failures | Business rejections are final, published to `trades.rejected`; technical failures retried 3x then dead-lettered |
| Error format | RFC 9457 `ProblemDetail`; 400 = malformed/invalid payload, 422 = business-rule rejection |
| Observability | Actuator health with liveness/readiness probes, Prometheus metrics, sanitised logs |
| Packaging | Multi-stage Dockerfile, non-root runtime user; `docker compose up` runs the whole stack |
| Security gate | Trivy on the **built JAR** and the **image**, failing on HIGH or CRITICAL ("blocker" = HIGH) |

Known, accepted limitation (document it, do not "fix" it): delivery is at-least-once and a same-version replay
can overwrite a later same-version correction, because equal versions replace and the message has no event id
or source timestamp. The production follow-up is an event id / source sequence number.

## 3. Business rules in detail (the contract)

- A1: A trade is identified by `tradeId`; the store keeps only the latest version (current state).
- A2: Lower version -> rejected (`LowerVersionException`). Same version -> replaces. Higher version -> replaces.
- A3: Maturity **equal to today is accepted**; only strictly before today is rejected (`MaturityDateInPastException`).
- A4: Maturity is checked **before** version, so a past-maturity trade never touches the stored record.
- A5: "Today" = `LocalDate.now(clock)`; zone from `tradestore.zone` (default `UTC`).
- A6: `createdDate` is optional; when absent, stamp today's business date; when present, keep as sent.
- A7: `expired` is owned by the store, never accepted from senders. New/replaced trades are stored `expired = false`.
- A8: Expiry marks `maturity_date < today AND expired = false` as expired. It runs daily at 00:00 in the business
  zone and once at start-up (configurable), and on demand via the API. It is idempotent.
- A9: An expired trade can be revived by a newer version with a future maturity (stored as not expired).
- A10: `version` is a positive integer. `tradeId`, `counterPartyId`, `bookId`, `maturityDate` are mandatory.
- A11: Dates on the wire are `dd/MM/yyyy`; stored as SQL `DATE`.
- Sample row T3 is resolved as two cases: rejected if it **arrives** after maturity; if stored before maturity,
  it is marked `Expired = Y` once maturity passes.

## 4. Project layout and classes (exact names)

Group `com.tradestore`, artifact `trade-store`, version `1.0.0-SNAPSHOT`. Base package `com.tradestore`.

```
src/main/java/com/tradestore/
  TradeStoreApplication.java        @SpringBootApplication @ConfigurationPropertiesScan
  api/TradeController.java          REST endpoints (section 5)
  api/TradeResponse.java            record; dates dd/MM/yyyy; expired rendered "Y"/"N"; static from(Trade)
  api/ApiExceptionHandler.java      @RestControllerAdvice -> ProblemDetail
  audit/Channel.java                enum REST, KAFKA
  audit/TradeAuditEvent.java        @Document("trade_audit") record (section 6)
  audit/TradeAuditRepository.java   MongoRepository; findByTradeIdOrderByReceivedAtAsc
  audit/TradeAuditService.java      accepted(), rejected(), history(); best-effort save + failure metric
  config/ClockConfig.java           @Bean Clock = Clock.system(properties.zone())
  config/KafkaConfig.java           topics, error handler, DLT recoverer, value serializer customizer
  config/SchedulingConfig.java      @EnableScheduling
  config/TradeStoreProperties.java  @ConfigurationProperties("tradestore") record (section 8)
  domain/Trade.java                 JPA @Entity @Table("trades"); full constructor + getters; equals/hashCode/toString
  domain/TradeRequest.java          inbound record with Bean Validation (section 6)
  domain/TradeRepository.java       JpaRepository<Trade,String> + upsertUnlessLowerVersion + markExpired
  domain/TradeValidator.java        maturity rule then version rule
  domain/RejectionReason.java       enum INVALID_PAYLOAD, LOWER_VERSION, MATURITY_DATE_IN_PAST
  domain/TradeRejectedException.java   base: tradeId + RejectionReason
  domain/LowerVersionException.java    "Trade %s version %d rejected: current version is %d"
  domain/MaturityDateInPastException.java "Trade %s rejected: maturity date %s is before today %s"
  domain/InvalidTradeException.java    "Trade %s rejected: %s" (sorted violations)
  domain/TradeNotFoundException.java   "Trade %s not found" (not a TradeRejectedException)
  domain/StrictIntegerDeserializer.java   rejects non-integer JSON tokens (e.g. 2.9)
  domain/StrictLocalDateDeserializer.java strict dd/MM/uuuu, regex [0-9]{2}/[0-9]{2}/[0-9]{4}, years 0001-9999
  messaging/TradePublisher.java     publish() keyed by tradeId; publishAndAwait() bounded wait
  messaging/TradePublishException.java  outcome FAILED | UNKNOWN
  messaging/TradeKafkaListener.java @KafkaListener on inbound topic
  messaging/TradeRejection.java     record published to trades.rejected
  service/TradeIngestionService.java single entry point for all channels
  service/TradeStoreService.java    validate + conditional upsert; get; getAll
  service/TradeExpiryService.java   expireMaturedTrades() @Transactional
  service/TradeExpiryScheduler.java @Scheduled daily + ApplicationReadyEvent start-up run
src/main/resources/application.yml
src/main/resources/db/migration/V1__create_trades.sql
```

Note: Spring Boot 4 uses **Jackson 3** (`tools.jackson.*`; custom deserializers extend `ValueDeserializer`).

## 5. REST API (base path `/api/v1/trades`)

| Method and path | Behaviour | Success |
|---|---|---|
| `POST /api/v1/trades` | Synchronous ingest via `TradeIngestionService` (channel REST) | `201`, `Location: /api/v1/trades/{tradeId}` (tradeId encoded as one path segment), body `TradeResponse` |
| `POST /api/v1/trades/async` | `TradePublisher.publishAndAwait` to Kafka (no validation here; the consumer validates) | `202`, body `{"tradeId": "...", "status": "QUEUED"}` |
| `GET /api/v1/trades` | All trades ordered by `tradeId` | `200` list of `TradeResponse` |
| `GET /api/v1/trades/{tradeId}` | One trade | `200` or `404` |
| `GET /api/v1/trades/{tradeId}/audit` | Audit events from MongoDB, oldest first | `200` list |
| `POST /api/v1/trades/expiry-run` | Runs the expiry sweep | `200` `{"expired": <count>}` |

Errors (`ApiExceptionHandler`, `spring.mvc.problemdetails.enabled: true`):
- `TradeRejectedException` -> `400` if reason `INVALID_PAYLOAD`, else `422`; title `Trade rejected`; properties
  `tradeId`, `reason`.
- `TradeNotFoundException` -> `404`.
- Unreadable JSON (bad date, fractional version, wrong type) -> `400` from Spring before ingestion (not audited).
- `TradePublishException` -> `503` with header `Retry-After: 1`; title `Publish outcome unknown` (UNKNOWN) or
  `Publish failed` (FAILED); properties `tradeId`, `outcome`.

Async semantics (document them): `202` means Kafka **confirmed the write** (`acks=all`), not that the trade is
stored yet. `publishAndAwait` sets **one deadline before calling `send()`** (so time blocked in the producer's
`max.block.ms` counts) and waits the remaining time:
- `send()` throws `KafkaException` -> `FAILED`.
- Future completes exceptionally -> `FAILED` (use the cause).
- Deadline passes or interrupted (restore the interrupt flag) -> `UNKNOWN` (the trade may still have been queued).
Resending the same payload converges only if no other same-version change was sent since (see the known limitation).

Other endpoints: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`,
`/actuator/prometheus`, `/swagger-ui.html` (springdoc).

## 6. Data contracts

`TradeRequest` (record):
```
tradeId        @NotBlank @Pattern("[A-Za-z0-9._-]{1,64}")
version        @NotNull @Positive Integer, StrictIntegerDeserializer
counterPartyId @NotBlank @Pattern("[A-Za-z0-9._-]{1,64}")
bookId         @NotBlank @Pattern("[A-Za-z0-9._-]{1,64}")
maturityDate   @NotNull LocalDate, @JsonFormat("dd/MM/yyyy"), StrictLocalDateDeserializer
createdDate    optional LocalDate, same format and deserializer
```
JSON example: `{"tradeId":"T1","version":1,"counterPartyId":"CP-1","bookId":"B1","maturityDate":"20/05/2030"}`

`TradeResponse`: `tradeId, version, counterPartyId, bookId, maturityDate (dd/MM/yyyy), createdDate (dd/MM/yyyy), expired ("Y"|"N")`.

`TradeRejection` (to `trades.rejected`, key = tradeId): `tradeId, version, reason, message, trade (the request), rejectedAt (Instant)`.

`TradeAuditEvent` (`@Document("trade_audit")`): `@Id id, @Indexed tradeId, version, channel, outcome (ACCEPTED|REJECTED), reason, message, payload (TradeRequest), receivedAt (Instant)`.

## 7. Persistence

`V1__create_trades.sql`:
```sql
CREATE TABLE trades (
    trade_id         VARCHAR(64) PRIMARY KEY,
    version          INTEGER     NOT NULL CHECK (version > 0),
    counter_party_id VARCHAR(64) NOT NULL,
    book_id          VARCHAR(64) NOT NULL,
    maturity_date    DATE        NOT NULL,
    created_date     DATE        NOT NULL,
    expired          BOOLEAN     NOT NULL DEFAULT FALSE
);
-- Supports the expiry sweep (maturity_date < today AND NOT expired) without a full scan.
CREATE INDEX idx_trades_active_maturity ON trades (maturity_date) WHERE expired = FALSE;
```

`TradeRepository.upsertUnlessLowerVersion(@Param("t") Trade t)` (native, `@Modifying(flushAutomatically = true, clearAutomatically = true)`), returns rows affected:
```sql
INSERT INTO trades (trade_id, version, counter_party_id, book_id, maturity_date, created_date, expired)
VALUES (:#{#t.tradeId}, :#{#t.version}, :#{#t.counterPartyId}, :#{#t.bookId},
        :#{#t.maturityDate}, :#{#t.createdDate}, :#{#t.expired})
ON CONFLICT (trade_id) DO UPDATE SET
    version = EXCLUDED.version, counter_party_id = EXCLUDED.counter_party_id, book_id = EXCLUDED.book_id,
    maturity_date = EXCLUDED.maturity_date, created_date = EXCLUDED.created_date, expired = EXCLUDED.expired
WHERE trades.version <= EXCLUDED.version
```
`markExpired(LocalDate today)`: JPQL `UPDATE Trade t SET t.expired = true WHERE t.maturityDate < :today AND t.expired = false` (same `@Modifying` flags). Plus `findAllByOrderByTradeIdAsc()`.

`TradeStoreService.store` (`@Transactional`): read current version (for a clear message) -> `validator.validate(request, currentVersion)` -> build `Trade` (createdDate default today, expired false) -> upsert; if it returns 0, throw `LowerVersionException` with the now-current version (a concurrent higher version won the race). The upsert is the safety net; the read is only for messaging.

## 8. Ingestion, messaging, audit, expiry, configuration

`TradeIngestionService.ingest(request, channel)`:
1. Bean Validation; violations -> `InvalidTradeException` (violations sorted by property path, joined `", "`).
2. `storeService.store`, `auditService.accepted`, counter `tradestore.trades.received{channel, outcome="accepted", reason="none"}`, info log.
3. On `TradeRejectedException`: `auditService.rejected`, counter with `outcome="rejected"`, `reason=<RejectionReason>`,
   warn log with CR/LF replaced by spaces in logged values, then rethrow.

`TradeKafkaListener.onTrade(TradeRequest)` on `${tradestore.topics.inbound:trades.inbound}`, group `${spring.kafka.consumer.group-id:trade-store}`:
- Calls `ingest(request, Channel.KAFKA)`.
- On `TradeRejectedException`: send a `TradeRejection` to the rejected topic (key = tradeId) and wait
  `.get(10, SECONDS)`. Interrupted -> restore flag, throw `KafkaException`; execution/timeout -> throw `KafkaException`
  so the container error handler retries and then dead-letters the original record (it is not committed as handled).
- Technical exceptions propagate to the error handler.

`KafkaConfig`:
- `NewTopic` beans: inbound and rejected with `partitions` from properties; dead-letter with 1 partition.
- `DefaultErrorHandler(new DeadLetterPublishingRecoverer(template, (r, ex) -> new TopicPartition(deadLetter, -1)), new FixedBackOff(1_000L, 3))`.
- `DefaultKafkaProducerFactoryCustomizer` setting a `DelegatingByTypeSerializer` (`byte[]` -> `ByteArraySerializer`,
  `Object` -> `JacksonJsonSerializer`) so the DLT recoverer can republish raw bytes of undecodable records.

`TradeAuditService.save`: `repository.save(event)`; on `RuntimeException` log an error (tradeId, outcome, exception)
**and** increment counter `tradestore.audit.failures{outcome}`; never rethrow (PostgreSQL stays the source of truth).

`TradeExpiryService.expireMaturedTrades()` (`@Transactional`): `markExpired(LocalDate.now(clock))`, increment
`tradestore.trades.expired` by the count, info log, return count.
`TradeExpiryScheduler`: `@Scheduled(cron = "${tradestore.expiry.cron:0 0 0 * * *}", zone = "${tradestore.zone:UTC}")`
and `@EventListener(ApplicationReadyEvent.class)` that runs only if `tradestore.expiry.run-on-startup`.

`TradeStoreProperties` record (`@DefaultValue` on every component):
```
zone: ZoneId = UTC
topics: inbound = trades.inbound, rejected = trades.rejected, deadLetter = trades.inbound.dlt, partitions = 3
expiry: cron = "0 0 0 * * *", runOnStartup = true
async: publishTimeout: Duration = 5s
```

`application.yml` (key settings):
```yaml
spring:
  application.name: trade-store
  threads.virtual.enabled: false
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/tradestore}
    username: ${DB_USERNAME:tradestore}
    password: ${DB_PASSWORD:tradestore}
    hikari.maximum-pool-size: 10
  jpa: { open-in-view: false, hibernate.ddl-auto: validate }
  flyway.enabled: true
  mongodb.uri: ${MONGO_URI:mongodb://localhost:27017/tradestore}
  data.mongodb.auto-index-creation: true
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: trade-store
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JacksonJsonDeserializer
        spring.json.value.default.type: com.tradestore.domain.TradeRequest
        spring.json.use.type.headers: false
        spring.json.trusted.packages: com.tradestore.domain
    producer:
      acks: all
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JacksonJsonSerializer
      properties: { enable.idempotence: true, spring.json.add.type.headers: false, max.block.ms: 5000 }
    listener.concurrency: 3
  mvc.problemdetails.enabled: true
tradestore:
  zone: ${TRADESTORE_ZONE:UTC}
  topics: { inbound: trades.inbound, rejected: trades.rejected, dead-letter: trades.inbound.dlt, partitions: 3 }
  expiry: { cron: "${TRADESTORE_EXPIRY_CRON:0 0 0 * * *}", run-on-startup: true }
  async.publish-timeout: ${TRADESTORE_ASYNC_PUBLISH_TIMEOUT:5s}
management:
  endpoints.web.exposure.include: health,info,metrics,prometheus
  endpoint.health.probes.enabled: true
springdoc.swagger-ui.path: /swagger-ui.html
```
Do not set the Kafka listener ack mode or `max.poll.interval.ms`; use Spring Kafka/Kafka defaults and do not claim
they are guarantees in the docs.

## 9. Maven build (`pom.xml`)

Properties: `java.version 17`, `cucumber.version 7.34.9`, `springdoc.version 3.2.0`, `jacoco.version 0.8.16`,
`pitest.version 1.30.2`, `pitest-junit5.version 1.2.3`, `dependency-check.version 12.2.2`,
`coverage.line.minimum 0.85`, `mutation.threshold 70`, `owasp.failBuildOnCVSS 9`.

`dependencyManagement` pins (fix HIGH CVEs in Kafka client compression libraries):
`at.yawk.lz4:lz4-java:1.11.4`, `org.xerial.snappy:snappy-java:1.1.10.9`.

Dependencies: `spring-boot-starter-webmvc`, `-validation`, `-data-jpa`, `-data-mongodb`, `-kafka`, `-actuator`,
`-flyway`, `flyway-database-postgresql`, `postgresql` (runtime), `micrometer-registry-prometheus`,
`springdoc-openapi-starter-webmvc-ui`. Test: `spring-boot-starter-test`, `spring-boot-restclient`,
`spring-boot-starter-webmvc-test`, `-actuator-test`, `-data-jpa-test`, `-data-mongodb-test`, `-flyway-test`,
`-kafka-test`, `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`,
`testcontainers-mongodb`, `testcontainers-kafka`, `awaitility`, `cucumber-java`, `cucumber-spring`,
`cucumber-junit-platform-engine`, `junit-platform-suite`.

Plugins:
- Surefire: excludes `**/*IT.java`, excludes the `cucumber` JUnit engine.
- Failsafe: includes `**/*IT.java`, goals `integration-test` + `verify`.
- JaCoCo: `prepare-agent` + `prepare-agent-integration`, merge `jacoco.exec` + `jacoco-it.exec` into
  `jacoco-merged.exec` at `post-integration-test`, report and **check LINE COVEREDRATIO >= 0.85** at `verify`;
  exclude `TradeStoreApplication` and `config/**`.
- PIT (+ junit5 plugin): target classes `com.tradestore.domain.*`, `service.*`, `api.*`, `messaging.*`;
  `mutationThreshold = ${mutation.threshold}`; run with `mvn test-compile org.pitest:pitest-maven:mutationCoverage`.
- Profile `owasp`: `dependency-check-maven` with `failBuildOnCVSS`, `nvdApiKeyEnvironmentVariable NVD_API_KEY`,
  formats HTML/JSON/SARIF, suppression file `owasp-suppressions.xml` (empty, with a comment), OSS Index analyzer off.

## 10. Tests (write them first; names are part of the deliverable)

Test helpers: `TestTrades` (fixed `TODAY`, `trade(id, version, maturity)` factories); `it/MutableClock` (settable
`Clock`, `setToday(LocalDate)` = start of day + 1 h, `reset()`); `it/TestInfrastructure` (`@TestConfiguration`
with `@ServiceConnection` containers `postgres:16-alpine`, `mongo:7.0`, `apache/kafka:3.8.0` and a `@Primary MutableClock`);
`it/IntegrationTest` meta-annotation (`@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureMetrics`,
`@AutoConfigureTestRestTemplate`, `@Import(TestInfrastructure.class)`, `@ActiveProfiles("it")`) so all ITs share one
context. `application-it.yml`: `tradestore.expiry.run-on-startup: false`, consumer group `trade-store-it`, log level WARN.

Unit / web slice (`mvn test`, no Docker):
- `TradeValidatorTest`: accepts new trade; accepts same or higher version; rejects lower version; accepts maturity
  today or later; rejects maturity before today; maturity is checked before version.
- `TradeStoreServiceTest`: stores new trade with today as createdDate and not expired; keeps supplied createdDate;
  replaces same version; rejects lower/past maturity without writing; rejects when a concurrent higher version wins
  the race (upsert returns 0); get throws when unknown; get/getAll delegate.
- `TradeIngestionServiceTest`: stores, audits and counts accepted trade; audits, counts and rethrows a business
  rejection; rejects an incomplete payload before touching the store; sanitises line breaks in logs.
- `TradeAuditServiceTest`: records accepted; records rejection with reason; audit failure does not propagate;
  audit failure is counted (`tradestore.audit.failures`, tag outcome); history in order.
- `TradeExpiryServiceTest`: marks trades matured before today and counts them; scheduler runs daily and on start-up
  when enabled; skips start-up run when disabled.
- `TradeKafkaListenerTest`: ingests from Kafka; publishes business rejection to the rejected topic; rejection
  publication failure propagates to the error handler; technical failures propagate; publisher keys by tradeId;
  `publishAndAwait` returns on confirm, reports UNKNOWN on timeout, counts time spent inside `send()` against the
  timeout, reports FAILED on an errored future and on a synchronous send exception.
- `TradeControllerTest` (`@WebMvcTest`): 201 + Location; Location encodes tradeId as one path segment; 422 for lower
  version and past maturity; 400 for invalid payload, fractional version, impossible date; 404; async 202 QUEUED;
  async 503 UNKNOWN (with `Retry-After: 1`) and 503 FAILED; list/get; audit and expiry-run endpoints.
- `TradeTest`: equals/hashCode/toString.

Integration (`mvn verify`, Testcontainers):
- `TradeRepositoryIT`: upsert inserts then replaces same/higher; never lets a lower version overwrite;
  `markExpired` flags only active trades matured before today.
- `TradeStoreFlowIT`: REST stores and audits; Kafka stores trades and publishes rejections; async REST routes
  through Kafka; poison message goes to the DLT without blocking the partition; fractional Kafka version and invalid
  calendar dates go to the DLT without audit; REST rejects invalid dates without audit; created-date year boundaries;
  expiry run marks matured trades; health and Prometheus metrics exposed; **same-version replay restores the older
  payload** (documents the known limitation).
- `ConcurrentVersionsIT`: 40 shuffled versions on 8 threads -> highest wins; **31 versions split across REST (even)
  and Kafka (odd)** -> eventually version 31 and stays 31. Assert only the version rule, not cross-channel ordering.
- `RejectionPublishFailureIT`: with `@TestPropertySource(properties = "tradestore.topics.rejected=invalid topic name")`,
  a past-maturity Kafka trade's rejection cannot be published; the original record is retried and lands on
  `trades.inbound.dlt` (key and payload intact; exception header mentions `TradeKafkaListener.onTrade`); nothing stored.
- `PerformanceSmokeIT`: publish 5,000 trades, all stored within 90 s, throughput > 100 trades/s (a regression guard,
  not a benchmark).
- Cucumber `CucumberAcceptanceIT` (`@Suite`, `@IncludeEngines("cucumber")`, `@SelectClasspathResource("features")`),
  `CucumberSpringConfiguration` (`@CucumberContextConfiguration @IntegrationTest`), `TradeStoreSteps`, and
  `features/trade_store.feature` with scenarios: brief sample trades before T1 matures (T2 v1 rejected LOWER_VERSION,
  audit "ACCEPTED, REJECTED"); same version replaces; higher version replaces; T3 arriving late rejected
  MATURITY_DATE_IN_PAST; stored T3 marked expired after the business date moves to 21/05/2014 and expiry runs;
  maturity boundary outline (today 14/05/2020: 15/05 and 14/05 accepted, 13/05 rejected).
  `junit-platform.properties`: glue `com.tradestore.acceptance`, plugins pretty + `html:target/cucumber-report.html`
  + `junit:target/cucumber-junit.xml`, `cucumber.junit-platform.discovery.as-root-engine=false`, publish quiet.

Gates: JaCoCo line coverage >= 85%, PIT mutation score >= 70%.

## 11. Containers and local stack

`Dockerfile` (multi-stage): build on `maven:3.9-eclipse-temurin-17` (`dependency:go-offline`, then
`package -DskipTests -Djacoco.skip=true`); runtime `eclipse-temurin:17-jre-alpine`, user/group `app`, `curl` for the
healthcheck, `EXPOSE 8080`, `HEALTHCHECK` on `/actuator/health/readiness`, `ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]`.

`docker-compose.yml`: `postgres:16-alpine` (db/user/password `tradestore`), `mongo:7.0`, `apache/kafka:3.8.0` in KRaft
mode (internal listener `kafka:29092`, external `localhost:9092`), and `app` (build `.`, env `DB_URL`, `MONGO_URI`,
`KAFKA_BOOTSTRAP_SERVERS=kafka:29092`, `depends_on` with `service_healthy`). Every service has a healthcheck; **all
ports bound to 127.0.0.1**. Start with `docker compose up -d --build --wait --wait-timeout 240`.

Also add `.devcontainer/devcontainer.json` (Java 17 + Maven + docker-in-docker), `.github/dependabot.yml`
(maven, github-actions, docker; weekly), `.dockerignore`, `.gitignore`.

## 12. CI/CD (`.github/workflows/ci.yml`)

Triggers: push to `main`, pull requests, manual. `permissions: contents: read`; concurrency cancels stale runs.
Jobs (Temurin 17, Maven cache):
1. `build-and-test`: `mvn -B -ntp verify`; upload surefire/failsafe/JaCoCo/Cucumber reports; line coverage in the step summary.
2. `mutation-tests`: PIT; upload `target/pit-reports`.
3. `oss-vulnerability-scan` ("fails on high/critical"): `mvn package -DskipTests -Djacoco.skip=true`, then
   `aquasecurity/trivy-action` `scan-type: rootfs`, `scan-ref: target`, `severity: HIGH,CRITICAL`, `exit-code: 1`.
   Scan the JAR, not `pom.xml` (a pom scan cannot resolve Spring Boot-managed or transitive versions).
   Then optional OWASP Dependency-Check (`mvn -Powasp verify -DskipTests`) only when the `NVD_API_KEY` secret exists;
   otherwise emit a warning **and write "SKIPPED" to `$GITHUB_STEP_SUMMARY`**. Upload the report.
4. `docker-image` (needs 1-3): build image, Trivy image scan `HIGH,CRITICAL` exit 1, push to GHCR only on `main`.
5. `smoke-test` (needs 4): compose up, POST a valid trade and GET it (`"expired":"N"`), POST a past-maturity trade and
   expect `422`, logs on failure, `docker compose down -v`.

## 13. Load test, Postman and docs

- `perf/nfr-load.js` (k6): `constant-arrival-rate`; env `BASE_URL` (default `http://localhost:8080`), `MODE`
  (`async` -> expects 202, else 201), `RATE` (default 200), `DURATION` (default 60s); unique trade ids per run;
  thresholds `http_req_failed < 1%`, `p(99) < 200 ms`, `checks > 99%`, `dropped_iterations < 1`.
- `postman/trade-store.postman_collection.json` + `postman/local.postman_environment.json` (`baseUrl =
  http://localhost:8080`): health (sets unique run ids); version rules (new 201, same 201, higher 201, lower 422
  LOWER_VERSION, get still v2); validation (past maturity 422, missing fields 400, unsafe id 400, fractional version
  400, impossible date 31/02/2030 400, unknown 404); async 202 then poll until 200; audit, list, expiry run,
  Prometheus. Each request has test assertions; runnable with Newman in Docker.
- `docs/ASSUMPTIONS.md`: sample-data reading (incl. the T3 resolution), A1-A20 covering sections 3, 5 and 8
  (including the replay limitation, async 202/503 semantics, best-effort audit with failure metric).
- `docs/NFR.md` table (target, how it is met, status): throughput >= 1,000 trades/s with 5,000 bursts; REST p99
  < 200 ms; per-trade ordering; lower version never overwrites; RPO 0 for accepted trades (acks=all, idempotence,
  offset after DB write); at-least-once with the same-version caveat; poison messages never block a partition;
  bounded async wait; 99.9% availability as design intent; best-effort auditability; deterministic "today";
  observability metrics incl. `tradestore.audit.failures`; supply chain gate HIGH/CRITICAL (Trivy enforced,
  Dependency-Check optional); least-privilege runtime; coverage/mutation gates; one-command portability; Flyway;
  partial index for expiry. Mark what is implemented vs design intent; never present local numbers as guarantees.
- `docs/TEST-STRATEGY.md`: table of layers (tooling, what it proves, where, where it runs).
- `docs/DESIGN-PRINCIPLES.md`: 18 one-line principles (one ingestion path; invariants in the database; reject,
  don't guess; machine-readable rejections; business vs technical failures; per-trade ordering; don't lose accepted
  trades; one system of record; time as a dependency; idempotent self-healing jobs; immutability and explicit
  dependencies; small single-purpose classes; schema as code; configuration over constants; observable by default;
  secure by default; automated quality gates; explicit trade-offs). Only claim gates that CI actually runs.
- `docs/adr/`: `0001-kafka-as-ingestion-backbone`, `0002-postgresql-state-mongodb-audit` (outbox as production
  follow-up), `0003-atomic-conditional-upsert`, `0004-clock-driven-expiry` (context, decision, consequences).
- `docs/diagrams/*.puml` (+ rendered SVGs): `component`, `class`, `sequence-ingest`, `sequence-expiry`, `pipeline`.
- `README.md`: overview, architecture diagram, run commands, API examples, configuration table, tests, CI job table
  (what fails the build), deployment notes; links to all docs.
- `local-run.md`: Windows PowerShell first (`Invoke-RestMethod`/`Invoke-WebRequest` helper, `curl.exe` never bare
  `curl`, quoted Maven `-D` args, Docker `--network "container:$(docker compose ps -q app)"` for Newman/k6), Bash in
  collapsible sections; steps for API, Postman/Newman, tests with HTML reports
  (`mvn surefire-report:report-only surefire-report:failsafe-report-only "-DlinkXRef=false"`), NFR demos
  (k6 sync/async, durability across app restart, DLT, concurrency, health/metrics, Trivy HIGH/CRITICAL on the JAR,
  non-root `id`), and troubleshooting (JDK vs JRE, `JAVA_HOME`).
- `AI_PROMPTS.md`: every prompt used, verbatim and in order, with one line on what each produced.

## 14. Build order and checkpoints

1. Skeleton: pom, application class, properties, clock config. Checkpoint: `mvn -q compile`.
2. Domain: request/entity/exceptions/validator/deserializers with unit tests. Checkpoint: `mvn test`.
3. Persistence: Flyway migration, repository, store service; `TradeRepositoryIT`. Checkpoint: `mvn verify`.
4. Ingestion + audit + metrics. Checkpoint: `mvn test`.
5. REST controller + exception handler. Checkpoint: `mvn test`.
6. Kafka publisher/listener/config/DLT; `TradeStoreFlowIT`, `ConcurrentVersionsIT`, `RejectionPublishFailureIT`. Checkpoint: `mvn verify`.
7. Expiry service + scheduler. Checkpoint: `mvn verify`.
8. Cucumber acceptance + performance smoke. Checkpoint: `mvn verify` (coverage gate green).
9. PIT. Checkpoint: `mvn test-compile org.pitest:pitest-maven:mutationCoverage` (>= 70%).
10. Dockerfile + compose. Checkpoint: `docker compose up -d --build --wait --wait-timeout 240` and
    `Invoke-RestMethod http://127.0.0.1:8080/actuator/health` shows `UP`.
11. CI workflow, Dependabot, devcontainer. Checkpoint: push a branch; all jobs green.
12. Trivy locally: `mvn -B package -DskipTests "-Djacoco.skip=true"` then
    `docker run --rm -v "${PWD}/target:/scan" aquasec/trivy:0.57.1 rootfs --scanners vuln --severity HIGH,CRITICAL --exit-code 1 /scan`.
13. k6, Postman, docs, diagrams, README, local-run, AI_PROMPTS. Checkpoint: Newman run passes every assertion.

## 15. Definition of done

- `mvn verify` green: all unit, integration and acceptance tests pass; line coverage >= 85%.
- PIT mutation score >= 70%.
- `docker compose up` brings the stack up healthy; the smoke calls behave as in section 12.
- CI green on a PR, including Trivy HIGH/CRITICAL on the JAR and image.
- Behaviour matches sections 3, 5 and 8 exactly; the replay limitation and async semantics are documented, not hidden.
- Docs, diagrams, Postman collection, k6 script, local-run guide and AI prompt log are present and consistent with the code.
