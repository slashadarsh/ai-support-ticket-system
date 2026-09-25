# Implementation Plan
Status: Reviewed
Traces to: all spec files. Each task is done when its tests pass and `/review-code` findings are fixed.

| ID | Milestone | Task | Spec refs | ACs | Tests | Done |
|---|---|---|---|---|---|---|
| T-01 | M1 Skeleton | Maven project (Boot 3.5.16, Spring AI 1.1.8), `application.yml`, `.env` import, profiles `dev`/`test`/`rageval` | architecture §2, §7 | AC-21, AC-22 | context loads | ☐ |
| T-02 | M1 Skeleton | Flyway V1 (ticket, comment, sequence) and V2 (vector_store + HNSW) | data-model §2, §4 | AC-11 | migrations run on `tickets_test` | ☐ |
| T-03 | M2 Tickets | `TicketStatus` transition table | state-machine §1 | AC-9, AC-10, AC-14 | `TicketStatusTest` (25 pairs) | ☐ |
| T-04 | M2 Tickets | Entities, repository (key sequence, search specification) | data-model | AC-2, AC-7, AC-8 | via T-06 ITs | ☐ |
| T-05 | M2 Tickets | `TicketService` (create, get, search, patch, transition, comment) + events | state-machine §2–3, api-contract §2 | AC-1..AC-10 | via T-06 ITs | ☐ |
| T-06 | M2 Tickets | `TicketController`, DTOs, `GlobalExceptionHandler` (ProblemDetail catalogue) | api-contract | AC-1..AC-13 | `TicketApiIT`, `StateMachineIT`, `TicketValidationWebTest` | ☐ |
| T-07 | M3 Ingestion | `TicketChunker` | rag-ingestion §2 | AC-15 | `TicketChunkerTest` | ☐ |
| T-08 | M3 Ingestion | `TicketIndexer` (REQUIRES_NEW) + `TicketChangeListener` (AFTER_COMMIT) + reindex-all | rag-ingestion §3–4, ADR-4 | AC-15, AC-20 | `IngestionIT` with `FakeEmbeddingModel` | ☐ |
| T-09 | M4 Ask | `RagProperties`, `QueryAnalyzer` | rag-api-contract §4, §6, ADR-7 | AC-21 | `QueryAnalyzerTest`, `RagPropertiesIT` | ☐ |
| T-10 | M4 Ask | `PromptFactory` + static system prompt (≥ 1,024 tokens) | rag-api-contract §2, ADR-5 | — | `PromptFactoryTest` (NFR-8, NFR-13) | ☐ |
| T-11 | M4 Ask | `AskService`, `CitationValidator`, `AskController`, 503 mapping | rag-api-contract §1, §3 | AC-16..AC-18 | `AskServiceTest`, `CitationValidatorTest`, `AskApiIT` | ☐ |
| T-12 | M4 Ask | Seed data (30 tickets) + `SeedDataLoader` | evaluation-strategy §1, rag-ingestion §5 | — | `RestartIT` (AC-11) | ☐ |
| T-13 | M5 Frontend | Vite app, `api.ts`, list/create/detail/ask pages, error display | ui-flow | AC-1..AC-8, AC-13 | Vitest page tests | ☐ |
| T-14 | M6 Evaluation | `RagEvaluationIT` (threshold sweep + golden questions), calibrate threshold, report | evaluation-strategy | AC-16..AC-18 | `mvn -Prag-eval verify` | ☐ |
| T-15 | M6 Wrap-up | `/review-code` pass, README, token-optimisation doc, final secret scan | test-strategy §2 (AC-19, AC-22, AC-23) | AC-19, AC-22, AC-23 | review checklist | ☐ |

Order: T-01 → T-15. Backend milestones M1–M4 before the frontend, so the UI is built against a tested API.
