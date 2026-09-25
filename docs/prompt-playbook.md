# Prompt Playbook — spec-driven sequence for this project

> **This is a recommended sequence, not a record.** The prompts actually sent are in [`prompt-log.md`](prompt-log.md). This playbook shows how the same result is driven prompt by prompt in a disciplined spec-driven workflow. It reuses the steering files, commands and artefacts that exist in this repository, and ends with the lessons from this build.

## Principles

1. **One phase per prompt.** Requirement → Specification → Plan → Implementation → Testing → Review → Fix. Never "build the application".
2. **Name the inputs and outputs.** Every prompt says which files to read and which single file (or task) to produce.
3. **The human decides, the AI proposes.** Open questions, trade-offs and thresholds are answered by the engineer in a follow-up prompt, with a reason.
4. **Every generation is followed by a review command** (`/review-spec`, `/review-code`, `/review-rag-output`), and the engineer accepts or rejects each finding with a reason.
5. **Verify instead of trusting.** Compile, run tests, inspect the jar (`javap`), check the docs, run the evaluation. Every verified AI mistake goes into `docs/ai-mistakes-log.md`.

## Sequence

### P0 — Steering files (before any spec)
```text
Set up reusable AI instructions for a Java 21 / Spring Boot 3 / Spring AI / PostgreSQL+pgvector / React project:
CLAUDE.md (workflow + hard constraints), .claude/rules/{java-springboot,testing,api-standards,rag-vector-store}.md,
.claude/commands/{review-code,review-spec,generate-tests,review-rag-output}.md, a documentation skill,
and a UserPromptSubmit hook that logs every prompt to docs/prompt-history.md and .specstory/history/.
No application code.
```
*Checkpoint:* read every rule file; edit anything you disagree with before continuing.

### P1 — Requirements
```text
Read the assessment brief and write spec/requirements.md using the documentation skill:
FR-n / NFR-n, every Core Acceptance Criterion as AC-n traced to the FR/NFR it verifies, the full state-machine
matrix, an Out-of-scope section (auth, agents, assistant side effects) and Open Questions with proposed defaults.
No design, no code.
```
```text
/review-spec spec/requirements.md
```
```text
Apply P1–P3. Record M-1 (the AC-17/AC-18 contradiction) in docs/ai-mistakes-log.md.
Decide the open questions: accept the defaults except OQ-12 — make re-ingestion synchronous after commit,
because deterministic tests and immediate freshness matter more than request latency at this scale.
Mark requirements.md Reviewed.
```

### P2 — Design specs (one prompt each, review after each)
```text
Write spec/architecture.md and spec/data-model.md from spec/requirements.md. Include decision records for:
chunking for ticket data (paragraph vs fixed-size vs semantic), embedding model (OpenAI small vs large vs local
Ollama, with cost/latency/quality), pgvector vs Chroma, the re-ingestion trigger, and prompt caching (check
OpenAI's minimum cacheable prompt length). Pin library versions from Maven Central — do not guess.
```
```text
Write spec/state-machine.md and spec/api-contract.md (all endpoints, JSON examples, validation, ProblemDetail catalogue).
```
```text
Write spec/rag-ingestion.md, spec/rag-api-contract.md and spec/evaluation-strategy.md, including a 30-ticket
golden dataset where every example question from the brief has a known answer, 6 out-of-scope questions,
one prompt-injection ticket, and pass thresholds.
```
```text
Write spec/ui-flow.md and spec/test-strategy.md mapping every AC to at least one test.
```
```text
/review-spec spec/  — cross-file contradictions, uncovered ACs, unverified technical claims.
```
*Checkpoint:* accept or reject each finding with a reason; commit "Step 2".

### P3 — Plan
```text
Create spec/tasks.md: 15 small, independently testable tasks (T-01…T-15) with spec refs, AC IDs and tests.
All tasks start unticked. Do not implement.
```

### P4 — Implementation (repeat per task)
```text
Implement T-05 (TicketService) only. Write the tests from spec/state-machine.md first, then the code,
run them and show the result. Flag any Spring / Spring AI API you have not verified against the 1.1.8 jar.
```
```text
/review-code
```
```text
Fix items 1 and 3. Item 2 is wrong because <reason>. Log item 1 as M-n with the compiler output as evidence.
```
*Checkpoint:* tick the task only after its tests pass; commit per milestone.

### P5 — RAG evaluation
```text
Seed tickets_test from the golden dataset, run the threshold sweep (0.20–0.60), and report hit@K and
out-of-scope leakage per threshold. Do not change the configuration yet.
```
```text
Set app.rag.similarity-threshold to 0.40 — the middle of the safe band 0.35–0.50 — not the band's upper edge,
so secondary relevant tickets survive. Update the spec's calibration rule accordingly.
```
```text
/review-rag-output for G1–G9 and N1–N6: split each answer into claims and check each against the retrieved
tickets. For every unsupported, incomplete or over-certain claim, identify the cause (retrieval, threshold,
chunking or prompt) and log it.
```
```text
Fix the partial-context failure: always add each retrieved ticket's SUMMARY chunk to the context (ADR-10),
add a regression test, and re-run the evaluation.
```

### P6 — Wrap-up
```text
Run /graphify on the repo. Then write docs/token-optimisation.md (steering-file caching, targeted evidence,
app-level prompt caching with cached-token evidence), the README, and run a final secret scan.
```

## Lessons from this build

**What worked**
- Steering files and review commands written first kept later output consistent (package layout, ProblemDetail errors, testing rules).
- "Verify, don't trust" caught real defects: a Spring AI constructor that no longer exists (M-3), unbounded provider retries that would have blocked requests for ~19 minutes (M-5), and three wrong RAG answers that passed every automated metric (M-9, M-10, M-11).
- The golden dataset plus threshold sweep turned "is the threshold right?" into a measured decision instead of a guess.

**What to do differently next time**
- **Keep decisions in the engineer's own prompts.** Here, several decisions were taken through multiple-choice answers and one "finish it" instruction. Writing them as prompts with reasons (like P1's decision prompt above) makes the reasoning visible in the history.
- **Keep prompts at task size.** Delegating Steps 2–6 in one instruction still produced reviewed, tested work, but the history lost the per-task review conversation that shows engineering judgement.
- **Run the independent spec review.** The cross-file `/review-spec` in Step 2 was skipped for time; two defects it would likely have caught surfaced later (M-5 retries, M-12 calibration rule).
- **Put timeouts and retry limits in the spec** whenever an external call runs inside a user request.
- **Audit answers claim by claim from the first evaluation run.** Metrics like hit@K and citation precision were all 1.00 while three answers were still wrong.
- **Use the session UI for progress** instead of status-check prompts; they add noise to the history without adding direction.
