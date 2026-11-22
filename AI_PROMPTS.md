# AI usage and prompts

The brief allows AI tools provided all prompts are shared. This document lists them.

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
