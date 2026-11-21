# AI usage and prompts

The brief allows AI tools provided all prompts are shared. This document lists them.

## Tool

**Devin** (Cognition AI), an autonomous AI software engineer. It was used to:
- review the brief and point out ambiguities (for example the T3 row), then draft assumptions, NFRs and the test strategy;
- generate the Spring Boot code, tests (test-first), Cucumber scenarios, PlantUML diagrams, ADRs, Docker/Compose
  files and the GitHub Actions pipeline;
- run the build and test suites locally (Testcontainers) and fix failures.

I set the direction (NFRs first, automation beyond regression, an explicit assumptions list, GitHub-based
delivery), reviewed every design decision and the code, and I own the submission.

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

<!-- Add any follow-up prompts (review comments, change requests) below, in order. -->
