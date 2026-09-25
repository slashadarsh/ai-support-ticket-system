# Prompt History

**Project:** AI-Powered Support Ticket System
**Workflow:** Requirement → Specification → Plan → Implementation → Testing → Review → Fix
**Assistant:** Claude Code in VS Code, steered by `CLAUDE.md` and `.claude/rules/`
**How to read each entry:** the prompt, then its result. The *Pattern* line names the prompting technique it shows.

| Phase | Prompts | Result | Commit |
|---|---|---|---|
| 0. Setup and AI steering | 1–5 | Stack decided; steering files, review commands, prompt logging; environment verified | `6661aaa` |
| 1. Requirements | 6–9 | 24 FRs, 13 NFRs, 23 ACs, 17 open questions decided; 1 AI mistake caught | `fd2d525` |
| 2. Specification and plan | 10–12 | 9 design specs with 10 decision records; 15-task plan; 2 AI mistakes caught | `7897952`, `dc9cf60` |
| 3. Implementation and review | 13–15 | Backend (119 tests) and frontend (8 tests); 4 AI mistakes caught | `3f77896`, `2f19680` |
| 4. RAG evaluation | 16–20 | Threshold calibrated; 3 wrong answers and 1 spec flaw found and fixed; all metrics 1.00 | `a52cce6` |
| 5. Delivery | 21–24 | Docs, public GitHub repo, code knowledge graph | later commits |

---

## Phase 0 — Setup and AI steering

### 1. Understand the brief before building
```text
@"Assessments .pdf" is the brief for an AI-powered support-ticket system
(Java 21, Spring Boot, Spring AI, PostgreSQL + pgvector, React).

Don't write application code yet. First:
1. Summarise the brief in 10 bullets: deliverables, hard requirements, acceptance criteria.
2. List the decisions I must make before we start (IDE workflow, LLM and embedding provider,
   vector store, frontend), each with your recommendation and its main trade-off.
Then wait for my answers.
```
**Result:** brief summarised; four decisions proposed with trade-offs.
*Pattern:* context first, explicit non-goal, human decision gate.

### 2. Decide, then set up the steering files
```text
Decisions: Claude Code in VS Code; OpenAI for chat and embeddings (text-embedding-3-small);
local PostgreSQL 15 + pgvector; React + Vite + TypeScript.

Set up reusable AI instructions before any spec:
- CLAUDE.md: the workflow (Requirement → Spec → Plan → Implement → Test → Review → Fix) and hard
  constraints (no secrets in git, no agent behaviour, state machine enforced in the backend).
- .claude/rules/: java-springboot.md, testing.md, api-standards.md, rag-vector-store.md.
- .claude/commands/: review-code, review-spec, generate-tests, review-rag-output
  (the last one audits assistant answers for hallucination, claim by claim).
- A documentation skill with templates for specs and for an AI-mistakes log.
- A UserPromptSubmit hook that logs every prompt to docs/ and .specstory/history/.
No application code.
```
**Result:** `CLAUDE.md`, 4 rule files, 4 commands, the documentation skill and the prompt hook, all tested.
*Pattern:* decisions stated with the task; reusable context written once and used by every later prompt.

### 3. Verify the environment
```text
Check my machine and list only the blocking gaps: JDK 21, PostgreSQL version, whether the
pgvector extension is installed, Node version, and whether .env exists. Check each one rather
than assuming. For each gap, give the single command I should run.
```
**Result:** JDK 21 was installed but not the default, so a project-scoped `JAVA_HOME` was recorded in `CLAUDE.md`. pgvector 0.8.1 was already installed. The hook now masks API keys.
*Pattern:* verify, don't assume.

### 4. Secrets and database, safely
```text
In .env, which values are placeholders I must replace, and which are defaults I can keep?
Don't ask me for any secret values. Give me the commands to create the database role and
database myself, and flag any privilege issues (for example, extensions that need a superuser).
```
**Result:** placeholders identified. The developer created the role and database. pgvector turned out not to be a *trusted* extension, so a superuser has to create it; this was recorded in `CLAUDE.md` so no migration would try.
*Pattern:* secrets stay out of the chat; ask for the risks, not only the steps.

### 5. Commit with checks
```text
Commit Step 0 to the local repository (no remote yet). Before committing, show me the staged
file list, confirm .env and local settings are excluded, and scan the staged diff for secrets.
Message: "Step 0: AI steering files, review commands, prompt logging".
```
**Result:** 18 files committed, secret scan clean → `6661aaa`.
*Pattern:* commands carry their own safety checks.

---

## Phase 1 — Requirements

### 6. Requirements spec
```text
Step 1 — Requirements. Read @"Assessments .pdf" and write spec/requirements.md using the
documentation skill.
- FR-n / NFR-n, each citing the PDF section it comes from; mark anything you add as (derived).
- All "Core Acceptance Criteria" as AC-n, in PDF order, each traced to the FR/NFR it verifies.
- The state machine as a full from→to matrix (allowed / rejected).
- Out of scope: auth, agent behaviour, assistant side effects (creating tickets, notifications).
- Open questions, each with a proposed default and what it affects.
Constraints: no design, no code.
Done when: every AC traces to a requirement and every requirement has a source.
```
**Result:** 24 functional and 13 non-functional requirements, 23 acceptance criteria, a 25-pair state matrix (5 allowed), 17 open questions.
*Pattern:* traceability and an explicit "done when".

### 7. Review the spec
```text
/review-spec spec/requirements.md
```
**Result:** three priority findings, including a contradiction: AC-17 required every answer to cite a ticket, while AC-18 required the "no relevant tickets" answer to cite none.
*Pattern:* a saved review command; the AI checks the AI.

### 8. Act on the review
```text
Apply P1–P3 as proposed. Log the AC-17/AC-18 contradiction as M-1 in docs/ai-mistakes-log.md,
with the evidence and how /review-spec caught it. Add a revision-history entry.
Leave the open questions alone; I'll decide them in my next message.
```
**Result:** requirements rev 2; first entry in the AI-mistakes log.
*Pattern:* an approval says what to change and what not to touch.

### 9. Decide the open questions
```text
Decisions on the open questions: accept the proposed defaults, except OQ-12. Re-ingest
embeddings synchronously right after the database commit, not asynchronously: deterministic
tests and immediate freshness matter more than a few hundred milliseconds of request latency
at this scale. Record the decisions and mark requirements.md as Reviewed.
```
**Result:** requirements rev 3, status Reviewed → `fd2d525`.
*Pattern:* the human owns the decision and writes down the reason.

---

## Phase 2 — Specification and plan

### 10. Architecture and data model
```text
Write spec/architecture.md and spec/data-model.md from spec/requirements.md. Include decision
records for: chunking of ticket data (paragraph vs fixed-size vs semantic), embedding model
(OpenAI small vs large vs local Ollama, with cost/latency/quality), pgvector vs Chroma, the
re-ingestion trigger, and prompt caching (check OpenAI's minimum cacheable prompt length).
Pin versions from Maven Central and confirm model availability with the project key — do not guess.
```
**Result:** decision records ADR-1 to ADR-9, with Spring Boot 3.5.16 and Spring AI 1.1.8 verified. The check showed that GPT-5 models reject `temperature: 0`, so `gpt-4.1-mini` was chosen.
*Pattern:* ask for trade-offs, and demand evidence for facts.

### 11. Contracts, RAG and test specs
```text
Write the remaining specs, each traced to requirement IDs:
- state-machine.md and api-contract.md (all endpoints, JSON examples, ProblemDetail error catalogue);
- rag-ingestion.md, rag-api-contract.md and evaluation-strategy.md, with a 30-ticket golden dataset
  where every example question from the brief has a known answer, 6 out-of-scope questions,
  one prompt-injection ticket, and pass thresholds;
- ui-flow.md and test-strategy.md mapping every AC to at least one test.
Then run /review-spec on spec/ for cross-file contradictions and fix what it finds.
```
**Result:** 9 spec files (about 1,000 lines) → `7897952`.
*Pattern:* the evaluation is designed before the code exists.

### 12. Plan
```text
Create spec/tasks.md: small, independently testable tasks (T-01…T-15), each with spec refs,
AC IDs, the tests to write and a done criterion. All tasks start unticked. Do not implement.
```
**Result:** 15 tasks in 6 milestones → `dc9cf60`.
*Pattern:* plan and build are separate steps.

---

## Phase 3 — Implementation, testing and review

### 13. Build the backend with guardrails
```text
Implement T-01…T-12 in order. For each task: tests first (from the spec), then code, then run
the tests. Check any Spring AI class or method against the 1.1.8 jar before using it.
Log every verified AI mistake with evidence. Stop and ask me only if a test can't be made to
pass or the spec needs to change.
```
**Result:** backend complete with 117 tests (unit, web-layer, and integration against a real PostgreSQL). Caught along the way:
- a `TokenTextSplitter` constructor that no longer exists in Spring AI 1.1.8 (M-3, compiler);
- a real ticket ID in the prompt's examples, which could bias citations (M-4, unit test);
- three wrong assumptions in AI-written tests (M-6).

*Pattern:* delegation with verification rules and a stop condition.

### 14. Review the code
```text
/review-code
```
**Result:** found that Spring AI's default retry policy (10 attempts, backoff up to 180 s) combined with synchronous re-indexing could block a ticket update for about 19 minutes when OpenAI is down (M-5). Fixed with bounded retries and timeouts, plus a regression test → `3f77896`.
*Pattern:* review after every milestone, even when the tests pass.

### 15. Frontend
```text
Implement T-13 from spec/ui-flow.md: list with search, status filter and pagination; create form;
detail page with edit, server-driven status buttons and comments; Ask page with sources and a
distinct no-match state. Use one API client that turns ProblemDetail into field errors and banners.
Tests: field errors (400), rejected transition (409), assistant unavailable (503), no-match.
```
**Result:** React + Vite + TypeScript UI, 8 tests, type check and build clean → `2f19680`.
*Pattern:* UI prompts name the error states, not only the happy path.

---

## Phase 4 — RAG evaluation

### 16. Measure before tuning
```text
Seed tickets_test with the golden dataset and run a retrieval-only threshold sweep from 0.20 to
0.60. Report hit@K for in-scope questions and how many out-of-scope questions still retrieve
chunks, per threshold. Don't change any configuration yet.
```
**Result:** recall 1.00 up to 0.50 and 0.33 at 0.55; off-topic leakage stops at 0.35. Safe band: 0.35–0.50.
*Pattern:* measure first, decide second.

### 17. Calibrate with a reason
```text
Set app.rag.similarity-threshold to 0.40, the middle of the safe band rather than its upper edge,
so secondary relevant tickets still reach the model. Update the calibration rule in
spec/evaluation-strategy.md: "highest threshold with hit@K ≥ 0.9" would have picked the cliff edge.
```
**Result:** threshold 0.40; spec rule corrected (M-12).
*Pattern:* the reason goes into the spec, not just the config.

### 18. Audit the answers, not only the metrics
```text
Run the golden questions against the real models, then /review-rag-output on each answer:
split it into claims and check every claim against the retrieved tickets. For anything
unsupported, incomplete or over-certain, name the cause (retrieval, threshold, chunking or prompt).
```
**Result:** all metrics were 1.00, yet two answers were wrong. One left out a matching CRITICAL ticket (M-9); one stated a suspected cause as fact (M-10). Both were fixed in the system prompt.
*Pattern:* probabilistic output needs claim-level review.

### 19. Fix the root cause
```text
The answer for "payment failures" says TKT-1003 has no resolution, but it does. Find out which
chunks were retrieved, fix the root cause rather than the wording, add a regression test, and
re-run the full evaluation.
```
**Result:** only TKT-1003's comments chunk had been retrieved (M-11). Fixed by always adding each retrieved ticket's summary chunk to the context (ADR-10). Final evaluation: every metric 1.00, and no prompt injection followed → `a52cce6`.
*Pattern:* give the observed symptom, and ask for the cause and a regression test.

### 20. Test the second guardrail
```text
Restart with APP_RAG_SIMILARITY_THRESHOLD=0.25 (no code change) and ask
"What is our refund policy for enterprise customers?" and
"What is the SLA for resolving critical payment tickets?".
Confirm the model refuses (CONTEXT_INSUFFICIENT) instead of inventing an answer.
```
**Result:** both refused, even though related tickets were retrieved; the configuration override also worked.
*Pattern:* test the failure paths on purpose.

---

## Phase 5 — Delivery

### 21. Documentation
```text
Write the README (setup, tests, API, configuration), docs/rag-evaluation-report.md and
docs/token-optimisation.md, citing the cached-token evidence from the logs. Tick the finished
tasks, run a secret scan and commit.
```
**Result:** docs written; prompt caching verified, with 1,664 of 1,910 prompt tokens (87%) served from cache.
*Pattern:* claims in docs point to evidence.

### 22. Publish
```text
Create a private GitHub repo slashadarsh/ai-support-ticket-system with the authenticated gh CLI
and push main. Then verify: visibility is private, all commits are present, .env is not in the repo.
```
**Result:** private repo live; `.env` confirmed absent.
*Pattern:* an outward-facing action ends with verification.

### 23. Map the codebase
```text
/graphify .
Then commit graphify-out/ (graph.json, GRAPH_REPORT.md, graph.html; not caches or local paths)
and push. Summarise the most-connected nodes and any health warnings.
```
**Result:** code knowledge graph with 679 nodes, 1,783 edges and 30 communities, built with 0 LLM tokens.
*Pattern:* tooling prompts say what to keep and what to report.

---

## Takeaways for learners

1. **Steering files first.** `CLAUDE.md` and `.claude/rules/` made short prompts work, because the context was already there.
2. **One phase per prompt, and a check after each one**: `/review-spec`, `/review-code`, `/review-rag-output`.
3. **The human decides and writes down why** (prompts 9 and 17).
4. **Verify, don't trust.** 12 real AI mistakes were caught by compilers, tests, reviews and evaluation; see [`ai-mistakes-log.md`](ai-mistakes-log.md).
5. **For AI features, metrics aren't enough.** The answers that scored 1.00 still needed a claim-by-claim audit.
