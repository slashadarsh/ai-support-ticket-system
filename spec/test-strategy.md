# Test Strategy
Status: Reviewed
Traces to: AC-1..AC-23, NFR-7..NFR-11, `.claude/rules/testing.md`, `evaluation-strategy.md`

## 1. Layers

| Layer | Tooling | Runs in | External calls |
|---|---|---|---|
| Unit | JUnit 5, AssertJ, Mockito | `mvn test` | none |
| Web slice | `@WebMvcTest` | `mvn test` | none (services mocked) |
| Integration | `@SpringBootTest(webEnvironment = RANDOM_PORT)` against local PostgreSQL `tickets_test` (ADR-9) | `mvn test` | none: `FakeEmbeddingModel` + mocked `ChatModel` |
| RAG eval | `@Tag("rag-eval")` | `mvn -Prag-eval verify` | real OpenAI |
| Frontend | Vitest + React Testing Library | `npm test` | none (fetch mocked) |

**`FakeEmbeddingModel`** (test source): deterministic hashed bag-of-words → 1536-dim unit vector. Texts sharing words get higher cosine similarity, so retrieval, filters and re-indexing can be tested for real against pgvector without OpenAI.

**Isolation:** integration tests truncate `comment`, `ticket`, `vector_store` and restart `ticket_key_seq` at 1001 before each test.

## 2. Acceptance criteria → tests

| AC | Test(s) |
|---|---|
| AC-1 create | `TicketApiIT.createsTicketWithKeyAndOpenStatus` (201, Location, `TKT-1001`, OPEN); UI: `CreateTicketPage.test.tsx` (submit → navigate) |
| AC-2 list | `TicketApiIT.listsNewestFirstWithPagination` |
| AC-3 view | `TicketApiIT.returnsDetailWithComments`, `…unknownKeyReturns404` |
| AC-4 update | `TicketApiIT.patchUpdatesOnlyProvidedFields` |
| AC-5 assignee | `TicketApiIT.patchChangesAndClearsAssignee` (`""` → null) |
| AC-6 comments | `TicketApiIT.addsCommentWithAuthorAndTimestamp` |
| AC-7 search | `TicketApiIT.searchMatchesTitleOrDescriptionCaseInsensitive`, `…unknownKeywordReturnsEmpty` |
| AC-8 filter | `TicketApiIT.filtersByStatus`, `…combinesStatusAndKeyword` |
| AC-9 valid transitions | `StateMachineIT` allowed pairs (5) |
| AC-10 invalid transitions | `StateMachineIT` rejected pairs (15 + 5 same-status) → 409, status unchanged; `…resolveWithoutNotesReturns409`; `…patchWithStatusFieldReturns400`; `…patchOnClosedReturns409` |
| AC-11 restart | `RestartIT`: start context, create ticket + comment, close context, start a new one, read both back plus their chunks |
| AC-12 validation | `TicketValidationWebTest` (`@WebMvcTest`, parameterised over every §2.4 violation → 400 + field error, service never called); `AskValidationWebTest` (blank / 2 / 501 chars → 400, `AskService` never called) |
| AC-13 UI errors | `CreateTicketPage.test.tsx` (400 → field messages), `TicketDetailPage.test.tsx` (409 → banner), `AskPage.test.tsx` (503 → banner) |
| AC-14 state-machine ITs | `StateMachineIT` (25 pairs, real PostgreSQL) + `TicketStatusTest` (unit, 25 pairs) |
| AC-15 embeddings stored | `IngestionIT.createStoresChunksWithAllMetadata` |
| AC-16 grounded answer | `AskServiceTest` (mocked ChatModel returns valid JSON → grounded); RAG eval G1–G9 |
| AC-17 citations | `CitationValidatorTest` (drops unretrieved IDs; inline unknown key → no-match; none left → no-match); RAG eval citation precision |
| AC-18 no-match | `AskServiceTest.noChunksAboveThresholdNeverCallsLlm` (verify ChatModel never invoked); `…answerableFalseReturnsNoMatch`; `…invalidJsonReturnsNoMatch`; RAG eval N1–N6 |
| AC-19 docs | review: ADR-1, ADR-2 in `architecture.md` |
| AC-20 freshness | `IngestionIT.updateCommentAndTransitionReplaceChunks` (asserts right after the 2xx response); `…failingEmbeddingKeepsOldChunksAndRequestSucceeds` |
| AC-21 configurable | `RagPropertiesIT` (`@SpringBootTest(properties = "app.rag.top-k=2")` → `retrieval.topK == 2` and ≤ 2 matches); `RagPropertiesValidationTest` (top-k 0 → startup fails) |
| AC-22 no secrets | review check: `git grep -nE "sk-[A-Za-z0-9_-]{20,}"` returns nothing; `.env` in `.gitignore` |
| AC-23 mistakes log | review: `docs/ai-mistakes-log.md` has ≥ 1 evidenced entry |

## 3. Other requirement tests

| Requirement | Test |
|---|---|
| NFR-7 no tools / single call | `AskServiceTest.sendsExactlyOneChatCallWithoutTools` (captures the `Prompt`; asserts one call and no tool callbacks in the options) |
| NFR-8 static prompt prefix | `PromptFactoryTest`: system message identical for two different questions/contexts; estimated ≥ 1,024 tokens; context appears only in the user message |
| NFR-11 AI down | `AskWebTest`: `AskService` throws the provider exception → 503 `ai-unavailable`; `TicketApiIT` unaffected |
| NFR-13 injection | `PromptFactoryTest`: ticket text wrapped in `<ticket>` blocks; RAG eval G9 |
| ADR-7 query analysis | `QueryAnalyzerTest`: R1/R2/R3 triggers, combinations, whole-word matching only (e.g. "unresolved" must not trigger R3) |
| ADR-1 chunking | `TicketChunkerTest` (rag-ingestion.md §7) |

## 4. Rules for every test

- Derived from the spec's acceptance criteria, not from the implementation (`/generate-tests`).
- Never assert exact LLM wording; assert `grounded`, citations, `noMatchReason`, and filters.
- Every bug fix adds a regression test that failed before the fix.
- A test that would still pass with the code under test deleted is rewritten.

## 5. Commands

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -Prag-eval verify
cd frontend && npm test
```
