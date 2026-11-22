# Test strategy

The initial implementation was written before its tests. The tests now document and exercise the expected behavior,
but the repository history does not demonstrate a red-green-refactor TDD workflow.

"Regression testing" here is not a separate suite: **every automated test below runs on every pull request and
push** and blocks the merge if it fails.

| Layer | Tooling | What it proves | Where | Runs in |
|---|---|---|---|---|
| Unit (behavior) | JUnit Jupiter 6, Mockito, AssertJ, parameterised tests | Each business rule including boundaries (maturity today−1 / today / today+1; version lower / same / higher; validation order; concurrent-race fallback) | `src/test/java/**/*Test.java` | `mvn test` (surefire) |
| Web slice | `@WebMvcTest`, MockMvc | HTTP contract: 201 / 202 / 400 / 404 / 422, ProblemDetail body, date format | `TradeControllerTest` | `mvn test` |
| Integration | Testcontainers (PostgreSQL 16, MongoDB 7, Kafka 3.8) | Real SQL upsert semantics, expiry query, Kafka → DB, rejected topic, dead-letter handling, audit in MongoDB, Prometheus metrics | `src/test/java/**/it/*IT.java` | `mvn verify` (failsafe) |
| Concurrency | 40 shuffled versions on 8 threads; 31 versions split across REST and Kafka | Highest version always wins; no lost update. No ordering between REST and Kafka is promised, so only the version rule is asserted | `ConcurrentVersionsIT` | `mvn verify` |
| Failure paths | Unusable rejected topic; mocked Kafka futures | A failed rejection publish is retried then dead-lettered; async publish timeout and errors map to `503 UNKNOWN / FAILED`; same-version replay behaviour (A14) | `RejectionPublishFailureIT`, `TradeKafkaListenerTest`, `TradeControllerTest`, `TradeStoreFlowIT` | `mvn test` / `mvn verify` |
| Acceptance (BDD) | Cucumber + Spring | The brief's sample table, written as business-readable scenarios | `src/test/resources/features/trade_store.feature` | `mvn verify` |
| Performance smoke | 5,000 trades over Kafka | Throughput floor, catches order-of-magnitude regressions | `PerformanceSmokeIT` | `mvn verify` |
| Coverage gate | JaCoCo (unit + IT merged) | ≥ 85% line coverage or the build fails | `pom.xml` | `mvn verify` |
| Mutation | PIT | Tests actually fail when the code is wrong (≥ 70% mutants killed) | `pom.xml` | CI job `mutation-tests` |
| OSS vulnerabilities | Trivy on the built JAR; optional OWASP Dependency-Check | Fails on HIGH or CRITICAL (Trivy); CVSS ≥ 9 when Dependency-Check runs | CI job `oss-vulnerability-scan` | CI |
| Container | Trivy image scan | No high or critical vulnerabilities in the runtime image | CI job `docker-image` | CI |
| Smoke (deployed stack) | `docker compose` + curl | The built image starts with real dependencies and serves the API | CI job `smoke-test` | CI |

## Running locally

```bash
mvn test                      # unit + web slice (no Docker needed)
mvn verify                    # + integration, acceptance, performance smoke, coverage gate (Docker needed)
mvn test-compile org.pitest:pitest-maven:mutationCoverage   # mutation report in target/pit-reports
NVD_API_KEY=... mvn -Powasp verify -DskipTests              # OWASP Dependency-Check
```

Reports (run `mvn surefire-report:report-only surefire-report:failsafe-report-only -DlinkXRef=false` after `mvn verify` for the first two): `target/reports/surefire.html`, `target/reports/failsafe.html`, `target/site/jacoco/index.html`, `target/cucumber-report.html`, `target/pit-reports/index.html`.
