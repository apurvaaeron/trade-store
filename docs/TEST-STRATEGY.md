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
| Concurrency | 40 shuffled versions on 8 threads | Highest version always wins; no lost update | `ConcurrentVersionsIT` | `mvn verify` |
| Acceptance (BDD) | Cucumber + Spring | The brief's sample table, written as business-readable scenarios | `src/test/resources/features/trade_store.feature` | `mvn verify` |
| Performance smoke | 5,000 trades over Kafka | Throughput floor, catches order-of-magnitude regressions | `PerformanceSmokeIT` | `mvn verify` |
| Coverage gate | JaCoCo (unit + IT merged) | ≥ 85% line coverage or the build fails | `pom.xml` | `mvn verify` |
| Mutation | PIT | Tests actually fail when the code is wrong (≥ 70% mutants killed) | `pom.xml` | CI job `mutation-tests` |
| OSS vulnerabilities | Trivy fs, OWASP Dependency-Check | Fails on critical (CVSS ≥ 9) vulnerabilities | CI job `oss-vulnerability-scan` | CI |
| Container | Trivy image scan | No critical vulnerabilities in the runtime image | CI job `docker-image` | CI |
| Smoke (deployed stack) | `docker compose` + curl | The built image starts with real dependencies and serves the API | CI job `smoke-test` | CI |

## Running locally

```bash
mvn test                      # unit + web slice (no Docker needed)
mvn verify                    # + integration, acceptance, performance smoke, coverage gate (Docker needed)
mvn test-compile org.pitest:pitest-maven:mutationCoverage   # mutation report in target/pit-reports
NVD_API_KEY=... mvn -Powasp verify -DskipTests              # OWASP Dependency-Check
```

Reports: `target/site/jacoco/index.html`, `target/cucumber-report.html`, `target/pit-reports/index.html`.
