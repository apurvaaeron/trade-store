# AI usage and prompts

The brief allows AI tools provided all prompts are shared. This document lists them.

The complete design is also consolidated into one build prompt, [docs/IMPLEMENTATION-PLAN.md](docs/IMPLEMENTATION-PLAN.md),
for rebuilding the solution from scratch with a local coding agent (for example in IntelliJ). It was written after
the implementation and checked against it (Prompt 21). The prompts below are the ones actually used to produce this repository.

## Tool

**Devin** (Cognition AI), an autonomous AI software engineer. It was used to:
- review the brief and point out ambiguities (for example the T3 row), then draft assumptions, NFRs and the test strategy;
- generate the Spring Boot code and tests, Cucumber scenarios, PlantUML diagrams, ADRs, Docker/Compose
  files and the GitHub Actions pipeline;
- run the build and test suites locally (Testcontainers) and fix failures.

I set the direction (NFRs first, automation beyond regression, an explicit assumptions list, GitHub-based
delivery). The implementation and design decisions were generated for my review; I have not yet reviewed every
decision or all of the code. I own the submission.

## Prompts (verbatim, in order)

**Prompt 1** (brief attached: *Trade Assignment - For Architect Lead role.docx*)

> This is the case study given by them for me to develop, I want to prepare some NFRs first and want to have not just
> regression tests but automation tests. Also since the requirements are not elaborated I want to list down my
> assumptions for fine grained behavior would that be a great idea?
>
> on github can we deploy a spring boot based application?

Devin replied with draft assumptions, NFRs, a test pyramid, GitHub deployment options, and asked how to proceed.

**Prompt 2** (option chosen)

> Devin builds the full repo (code, TDD tests, pipeline, PlantUML, prompts doc) for me to review

**Prompt 3** (session setting, not about the solution)

> raise the limit

**Prompt 4**

> raise and keep going

**Prompt 5**

> is it done?

**Prompt 6** (local run guide)

> continure on devin, create a local-run.md for me to test the app locally

**Prompt 7**

> Is the case study complete?

**Prompt 8** (account access, not about the solution)

> Give e the git login commands

**Prompt 9** (Postman collection)

> do we have a postman collection for testing it?

**Prompt 10** (unrelated account setting; not actioned in the repository)

> turn off ChatGPT token sharing

**Prompt 11**

> check now, do we have a postman collection for testing it?

**Prompt 12**

> goahead

Devin added the `postman/` collection and local environment, and verified them with Newman against the running stack.

**Prompt 13** (test reports and NFR demo)

> Also I would need steps in ocal run to run Junit and integration tests with test reports , I want to demonstrate NFRs n my local machine, need steps for that too

Devin added test-report and NFR demonstration steps to `local-run.md`, plus the k6 load script `perf/nfr-load.js`, and ran each step against the local stack.

**Prompt 14** (PowerShell compatibility)

> This command is asking me to supply url curl -i http://127.0.0.1:8080/actuator/health

> Change local run file for windows powershell compatible

Devin rewrote `local-run.md` with Windows PowerShell commands first and Bash alternatives alongside, and ran the PowerShell commands in PowerShell 7 against the local stack.

**Prompt 15** (code review)

> Just check once the Java code quality and coding standards and explain why we have done thngs at code leve the way we did

Devin ran SpotBugs, PMD and Checkstyle and explained the code-level design choices.

**Prompt 16** (design principles)

> List my concise design principles in a separate file

Devin added `docs/DESIGN-PRINCIPLES.md`.

**Prompt 17** (code review validation)

> Found this in code review, just check validity of this review, don't change code before checking with me - P1: Kafka replay can restore stale same-version data.
> - P2: The `/async` REST path still blocks with `.join()`.
> - P2: Kafka listener rejection handling blocks for up to 10 seconds.
> - P2: Audit database failures are swallowed, risking unobservable MongoDB audit loss.
> - Coverage: Missing replay, publish-failure, slow-broker, and cross-channel ordering tests.
> Evidence is in `TradeKafkaListener.java`, `TradeStoreService.java`, `TradeRepository.java`, `TradeController.java`, `TradeAuditService.java`, and `.github/workflows/ci.yml`.

Devin checked each finding against the code and tests without changing anything.

**Prompt 18** (refined review)

> Don't do anything yet, here what i think about your table above - Yes—assuming A14/A20 and the test coverage you describe are accurate, this is a sensible refinement. Reclassifying replay as a documented trade-off and lowering it to P2 for a case study is reasonable: equal-version replacement is required by the brief, and the current message shape can't distinguish a replay from a correction.
> I'd qualify a few points:
> - `.join()` makes the endpoint blocking. A 5-second timeout and explicit error mapping are sensible, but a timeout can leave the publish outcome uncertain; clients may retry a message Kafka already accepted. Also, `202 Accepted` doesn't itself promise broker persistence.
> - Waiting on rejection publication only prevents loss if the listener's acknowledgment/retry configuration keeps the original offset uncommitted on publish failure. The five-minute `max.poll.interval.ms` is commonly the default, not a guarantee.
> - If rejection-publish failure is already covered by a unit test, remove it from the coverage gaps; focus on the untested timeout and replay scenarios. A concurrent REST/Kafka test should assert the intended version invariant, not deterministic cross-channel ordering if none is promised.
> - The audit concern is better described as no metric/alert for best-effort audit loss, not "silent" failure, if errors are logged as stated.
> The table also omits the earlier CI concern: Dependency-Check reportedly succeeds without `NVD_API_KEY`, and the scan doesn't explicitly gate blocker findings.

Devin verified the acknowledgment defaults and the CI behaviour (Dependency-Check skipped without the key).

**Prompt 19** (recommendations)

> My recommendations, please double check:
> - Keep replay as a documented limitation and rate it P2 for this case study; it remains a real production data-integrity risk if same-version corrections matter.
> - Treat `.join()` as an intentional blocking contract, but add a bounded wait and an explicit "publish outcome unknown" response. Longer term, an event ID/idempotency key is safer than relying on identical payloads.
> - Keep synchronous rejection publishing if preserving the rejection is the priority. Add an integration test proving a failed rejection publish retries and ultimately reaches the DLT; don't present Kafka's default five-minute poll interval as guaranteed.
> - Describe audit as best-effort with error logging, and recommend a failure metric/alert rather than calling it silent.
> - For CI, I recommend Trivy as the documented enforced gate at HIGH and CRITICAL, with "blocker" explicitly mapped to those severities. That meets the stated intent without making the build depend on an NVD key. If Dependency-Check remains optional, make that clear.
> - Focus remaining tests on async publish timeout/outcome handling, replay after a same-version correction, and cross-channel version invariants—not deterministic ordering between REST and Kafka.

Devin ran Trivy at HIGH/CRITICAL, found that the pom scan misses managed dependencies and that the JAR had 3 HIGH findings (lz4-java, snappy-java).

**Prompt 20** (implement)

> Go ahead and implement

Devin added the bounded async wait with `503 UNKNOWN / FAILED`, the `tradestore.audit.failures` metric, the lz4-java/snappy-java upgrades, the Trivy HIGH/CRITICAL gate on the JAR and image, and the replay, cross-channel and rejection-publish-failure tests.

**Prompt 21** (consolidated implementation plan)

> Now I want you to create a full prompt for this whole case study for developer who uses a local coding agent in intellj and it should be such a way as if I have researched and planned it first and built the right prompt before passing to the local agent to build the entire solution from scratch, give me the prompt file here for download, there must be no conflicts with our actual implementation

Devin wrote [docs/IMPLEMENTATION-PLAN.md](docs/IMPLEMENTATION-PLAN.md) from the current code, configuration, tests and docs.

**Prompt 22** (submission email and plan in the repo)

> I tested now give me the email content again, I intend to attach this prompt as well with the email, stating the research I have done with devin AI first to prepare a full implementaation plan

> Yes do that and give me the email content

Devin drafted the submission email, added the plan to `docs/` and linked it from this file and the README.
