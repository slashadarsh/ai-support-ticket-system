# Testing Guidelines

## Pyramid
| Layer | Tool | What |
|---|---|---|
| Unit | JUnit 5 + AssertJ + Mockito | `TicketStatus` transitions, services, chunker, prompt builder |
| Slice | `@WebMvcTest`, `@DataJpaTest` | controllers (validation, error mapping), repositories |
| Integration | `@SpringBootTest` against the local PostgreSQL database `tickets_test` (ADR-9 — Docker is not used) | state machine end-to-end, persistence, ingestion |
| RAG evaluation | JUnit tagged `@Tag("rag-eval")` against a seeded golden dataset | retrieval hit-rate, grounding, no-match behaviour |
| Frontend | Vitest + React Testing Library | forms, error display |

## Rules
- Test names describe behaviour: `shouldRejectTransitionFromClosedToOpen`.
- The state machine is tested **exhaustively**: every (from, to) pair in a `@ParameterizedTest` — allowed pairs succeed, all others return 409 via the API.
- No real OpenAI calls in unit/slice/integration tests. Mock `ChatModel`; use the deterministic `FakeEmbeddingModel` (hashed bag-of-words, 1536 dims) so pgvector retrieval is tested for real.
- RAG eval tests *do* call the real model; they are excluded from `mvn test` and run via `mvn verify -Prag-eval` with `OPENAI_API_KEY` set.
- Every bug fix gets a regression test that failed before the fix.
- Don't assert on exact LLM wording. Assert on structure: cited ticket IDs, `grounded` flag, no IDs outside the retrieved set.

## AI-generated tests
- Review generated tests for: tautologies (asserting mocks return what they were told), missing negative cases, and tests that pass with the implementation deleted.
