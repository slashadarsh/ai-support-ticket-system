# AI Mistakes Log

Mistakes or incorrect suggestions made by the AI assistant that were caught during development —
wrong code **and** ungrounded / hallucinated assistant answers. Format: see `.claude/skills/documentation/SKILL.md`.

<!-- Add entries as M-1, M-2, ... Only record real, verified mistakes with evidence. -->

## M-1: Contradictory citation requirement in requirements spec
- Phase: spec
- What the AI produced: in `spec/requirements.md` rev 1, FR-20 said "Every answer cites the ticket key(s) it was built from", and AC-17's pass condition said "Every answer lists ≥1 cited ticket key". The same draft required the no-match response (FR-21 / AC-18) to "cite no tickets".
- Why it was wrong: both conditions can't hold together. A no-match response is also an answer, so any implementation fails either AC-17 or AC-18. A test written from AC-17 would also push the model to produce a citation for out-of-scope questions, which is exactly the fabrication the PDF forbids.
- How it was caught: `/review-spec spec/requirements.md` (Contradictions check), before any design or code was written.
- Fix: FR-20 / AC-17 now apply only to `grounded: true` responses. The glossary defines the no-match response as `grounded: false` with an empty citation list (rev 2).
- Prevention: when writing "every X" requirements, check them against the no-match/empty case. `/review-spec` stays mandatory before a spec moves from Draft to Reviewed.

## M-2: Unverified claim about PostgreSQL extension privileges (architecture draft)
- Phase: spec
- What the AI produced: ADR-3 draft said Spring AI's schema initialisation "runs `CREATE EXTENSION` statements, which the non-superuser app role cannot do".
- Why it was wrong: `CREATE EXTENSION IF NOT EXISTS` on an extension that is already installed is a no-op and needs no superuser. The claim was stated as fact without checking. (The real facts, checked: `vector.control` has no `trusted = true`, so only *creating* pgvector needs a superuser.)
- How it was caught: self-review of the ADR before committing Step 2.
- Fix: ADR-3 now gives the accurate reason for Flyway-owned schema (one versioned place; the app role needs no extension privileges).
- Prevention: technical claims in ADRs must cite what was checked (file, jar, docs); CLAUDE.md now records the verified pgvector facts.

## M-3: Hallucinated / outdated Spring AI API — `TokenTextSplitter` constructor
- Phase: code
- What the AI produced: `new TokenTextSplitter(500, 350, 5, 10_000, true)` in `TicketChunker` (a 5-argument constructor from older Spring AI versions).
- Why it was wrong: Spring AI 1.1.8 has no such constructor. Compiler: `no suitable constructor found for TokenTextSplitter(int,int,int,int,boolean)`; `javap` on `spring-ai-commons-1.1.8.jar` shows only `()`, `(boolean)`, a 6-argument constructor and `builder()`.
- How it was caught: first `mvn compile`.
- Fix: `TokenTextSplitter.builder().withChunkSize(500)…build()`, verified against the jar with `javap` instead of re-guessing.
- Prevention: `/review-code` item 4 (flag unverified APIs); versions are pinned and checked on Maven Central (architecture §2).

## M-4: System prompt examples used a real dataset ticket ID
- Phase: code (prompt)
- What the AI produced: the static system prompt (`prompts/ask-system.st`) used `TKT-1001` as its example id ("for example [TKT-1001]"). TKT-1001 is a real ticket in the seed/golden dataset.
- Why it was wrong: an id repeated in every system prompt biases the model toward citing it, a subtle source of ungrounded citations. It also made a test assertion ("system prompt contains no ticket data") fail.
- How it was caught: `PromptFactoryTest.shouldKeepTheSystemMessageStaticAndFirst` failed.
- Fix: example ids changed to the unused TKT-2001 range; the test now asserts the prompt contains no `TKT-10xx` id.
- Prevention: the regression assertion stays in `PromptFactoryTest`.

## M-5: Unbounded provider retries would block ticket updates for ~19 minutes
- Phase: code / design review
- What the AI produced: the design made re-indexing synchronous inside the user's request (OQ-12, ADR-4) and relied on Spring AI's default HTTP/retry behaviour for OpenAI calls.
- Why it was wrong: Spring AI 1.1.8's defaults (verified with `javap` on `SpringAiRetryProperties`): `maxAttempts = 10`, backoff `2000 ms × 5` capped at `180000 ms`, which is about 19 minutes of waiting. With OpenAI down, every PATCH/transition/comment would hang, and `/ask` would not return its 503 (NFR-11) in any useful time. Neither the spec nor the code accounted for this.
- How it was caught: `/review-code` checklist pass on the finished backend (resilience of provider calls).
- Fix: `spring.ai.retry.max-attempts=2`, backoff 500 ms ×2 capped at 2 s, HTTP connect/read timeouts 5 s / 30 s (`application.yml`). Regression test `ProviderRetryIT` asserts exactly 2 attempts and < 5 s.
- Prevention: any synchronous external call inside a request needs explicit timeout and retry limits in the spec.

## M-6: AI-written tests with wrong assumptions
- Phase: test
- What the AI produced: (a) `RagPropertiesIT` set `app.rag.top-k=0` via `SpringApplicationBuilder.properties(...)`, expecting startup to fail; (b) it expected `similarity-threshold=0.0` to return every ticket for an unrelated question; (c) it asked a question that reached the LLM without stubbing the mocked `ChatModel`.
- Why it was wrong: (a) `builder.properties()` sets *default* properties, which `application.yml` overrides, so the bad value never applied; (b) pgvector filtering is strict (`distance < 1 − threshold`), so a vector with similarity exactly 0 is excluded even at threshold 0; (c) the mock returned `null`, causing a 500.
- How it was caught: the tests failed on first run (115/117 passing).
- Fix: pass `--app.rag.top-k=0` as a command-line argument; use a question that shares words with the tickets; stub the model.
- Prevention: `.claude/rules/testing.md` already asks to review AI-generated tests; failing-first runs caught all three.

## M-7: Plan generated with every task already marked done
- Phase: plan
- What the AI produced: `spec/tasks.md` was first written with ✅ in the Done column for all 15 tasks, before any code existed.
- Why it was wrong: a false progress report; anyone reading the plan would believe the work was finished.
- How it was caught: reading the generated file before committing Step 3.
- Fix: all tasks reset to ☐; they are ticked only after their tests pass.

## M-8: Environment advice given without checking the machine
- Phase: setup
- What the AI produced: "install pgvector: `brew install pgvector`" for the local PostgreSQL 15.
- Why it was wrong: pgvector 0.8.1 was already available (`pg_available_extensions`); the advice was not grounded in the actual environment.
- How it was caught: checking the database before running the command.
- Fix: no install; CLAUDE.md records the verified environment (JDK 21 path, pgvector version, trust auth).
