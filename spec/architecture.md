# Architecture
Status: Reviewed
Traces to: NFR-1..NFR-13, FR-13..FR-24, AC-19

## 1. Context

```mermaid
flowchart LR
    U[Support agent<br/>browser] -->|HTTP/JSON| FE[React SPA<br/>Vite + TS]
    FE -->|/api/*| BE[Spring Boot app]
    BE -->|JPA / JDBC| PG[(PostgreSQL 15<br/>ticket, comment<br/>+ pgvector vector_store)]
    BE -->|Embeddings API| OE[OpenAI<br/>text-embedding-3-small]
    BE -->|Chat Completions| OC[OpenAI<br/>gpt-4.1-mini]
```

One deployable backend, one database. The vector store lives in the same PostgreSQL database as the tickets (ADR-3).

## 2. Stack and versions

Versions were checked on Maven Central and against the project's OpenAI key on 2026-09-25. They are not guessed.

| Concern | Choice | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.5.16 |
| AI | Spring AI (`spring-ai-starter-model-openai`, `spring-ai-starter-vector-store-pgvector`) | 1.1.8 (BOM) |
| Database | PostgreSQL (Homebrew) + pgvector | 15.14 + 0.8.1 |
| Migrations | Flyway | managed by Boot |
| API docs | springdoc-openapi | latest 2.x compatible with Boot 3.5 |
| Frontend | React + Vite + TypeScript | current stable |

Spring AI 2.0.x is also released, but it requires Spring Boot 4. NFR-1 and `.claude/rules/java-springboot.md` fix Spring Boot 3 / Spring AI 1.x, so 1.1.8 is used.

## 3. Backend modules (package-by-feature)

```
com.example.tickets
├── ticket/    Ticket, TicketStatus (state machine), TicketService, TicketController, DTOs, TicketRepository
├── comment/   Comment, CommentRepository (comments are created through TicketService)
├── ai/
│   ├── ingest/    TicketChangedEvent, TicketIndexer, TicketChunker
│   ├── ask/       AskController, AskService, QueryAnalyzer, PromptFactory, CitationValidator
│   └── config/    RagProperties (@ConfigurationProperties "app.rag")
├── seed/      SeedDataLoader (loads seed/tickets.json when enabled and the DB is empty)
└── common/    GlobalExceptionHandler (ProblemDetail), exceptions, PageResponse
```

## 4. Key flows

### 4.1 Ticket write → re-index (FR-16, OQ-12)

```mermaid
sequenceDiagram
    participant C as TicketController
    participant S as TicketService (@Transactional)
    participant DB as PostgreSQL
    participant L as TicketIndexer (@TransactionalEventListener AFTER_COMMIT)
    participant V as PgVectorStore
    C->>S: create / update / comment / transition
    S->>DB: write ticket rows
    S-->>S: publish TicketChangedEvent(ticketKey)
    S->>DB: COMMIT
    DB-->>L: after commit (same thread)
    L->>DB: load ticket + comments (new REQUIRES_NEW tx)
    L->>V: embed chunks, delete old chunks, insert new chunks
    L-->>C: return (errors are logged, never thrown)
    C-->>C: 2xx response
```

### 4.2 Ask (FR-17..FR-21)

```mermaid
sequenceDiagram
    participant A as AskController
    participant Q as QueryAnalyzer
    participant V as PgVectorStore
    participant P as PromptFactory
    participant M as ChatModel (OpenAI)
    participant CV as CitationValidator
    A->>Q: question
    Q-->>A: filter (ticket keys / priority / status) + threshold mode
    A->>V: similaritySearch(question, topK, threshold, filter)
    alt no chunk passes
        A-->>A: no-match response (LLM NOT called)
    else chunks found
        A->>P: static system prompt + delimited context + question
        P->>M: one chat call, temperature 0, JSON output, no tools
        M-->>A: {answerable, answer, citedTicketIds}
        A->>CV: keep only cited IDs that were retrieved
        CV-->>A: grounded answer, or no-match if none valid / answerable=false
    end
```

## 5. RAG pipeline mapping (PDF "Basic RAG Flow")

| PDF step | Component |
|---|---|
| Support tickets → knowledge documents | `TicketChunker` builds Spring AI `Document`s from a ticket + comments |
| Chunk | `TicketChunker` (ADR-1) |
| Generate embeddings | `PgVectorStore.add` → `EmbeddingModel` (OpenAI, ADR-2) |
| Vector store | `vector_store` table, pgvector HNSW cosine (ADR-3) |
| User question → similarity search | `AskService` + `QueryAnalyzer` → `VectorStore.similaritySearch(SearchRequest)` |
| Relevant tickets → LLM + context | `PromptFactory` (ADR-5) |
| Grounded answer → ticket sources | `CitationValidator` + response `citations[]` (ADR-8) |

## 6. Decision records

### ADR-1 Chunking strategy for ticket data (NFR-5, AC-19)

**Context.** Ticket text is short and strongly structured: a title, a description (≤ 5000 chars), optional resolution notes (≤ 5000 chars) and a list of comments (≤ 2000 chars each). Questions are about problems, causes and resolutions of specific tickets. Every retrieved chunk must be attributable to exactly one ticket, so it can be cited.

| Option | Pros | Cons for ticket data |
|---|---|---|
| Fixed-size (e.g. 500 tokens, 50 overlap) over the concatenated ticket | Simple, predictable sizes | Cuts across field boundaries (half a description + half a comment). Most tickets are smaller than one chunk anyway, so the "strategy" does nothing for them and badly splits the rest. |
| Paragraph-based | Follows the author's structure | Descriptions are often one paragraph; comments like "Looking into it" become tiny, noisy chunks that rank highly on generic questions. |
| Semantic splitting (embedding-similarity breakpoints) | Topic-coherent chunks for long documents | Needs extra embedding calls on every re-index; boundaries are non-deterministic and hard to test; the benefit shows on long documents, not 1–2 k-token tickets. |
| **Structure-aware (field-based) with a size fallback** ✅ | Chunks follow the ticket's own structure; deterministic; every chunk belongs to one ticket | Needs a small custom chunker |

**Decision.** Structure-aware chunking:
1. **SUMMARY chunk** — header + title + description + resolution notes. The problem and its resolution stay together, which is what Q1–Q3 ask about.
2. **COMMENTS chunk(s)** — all comments in time order, packed into chunks of ≤ 400 tokens, **splitting only at comment boundaries**. This avoids one-line noise chunks.
3. **Fallback** — any single section still over ~500 tokens is split with Spring AI `TokenTextSplitter` (≈ 500 tokens); the header is repeated on every piece.
4. **Header** on every chunk: `[TKT-1001 | PAYMENT | HIGH | RESOLVED | assignee: Priya] <title>`. Each chunk is self-describing when retrieved on its own, and status/priority words in the header help the LLM answer Q4/Q5.

**Consequences.** A typical ticket produces 1–2 chunks. Re-indexing a ticket is cheap. Chunk sizes stay well inside the embedding model's input limit. Details and examples: `spec/rag-ingestion.md`.

### ADR-2 Embedding model (NFR-5, AC-19)

| Option | Dimensions | Cost (list price at time of writing) | Latency | Quality (published MTEB avg.) | Other |
|---|---|---|---|---|---|
| **OpenAI `text-embedding-3-small`** ✅ | 1536 | ≈ $0.02 / 1M tokens | ≈ 100–300 ms per API call (network) | ≈ 62.3 | No local infra; ticket text leaves the machine |
| OpenAI `text-embedding-3-large` | 3072 (can be shortened) | ≈ $0.13 / 1M tokens (6.5×) | similar | ≈ 64.6 | 3072 dims exceed pgvector's **2000-dimension HNSW limit** for `vector`: needs `halfvec` or the `dimensions` parameter |
| Local Ollama `nomic-embed-text` | 768 | $0 (own hardware) | ≈ 20–50 ms locally on Apple Silicon; slower on CPU | ≈ 62 | Data stays local; the Ollama service must run everywhere the app runs |

**Scale.** ~30 seed tickets × ~2 chunks × ~250 tokens ≈ 15 k tokens per full re-index, i.e. ≈ $0.0003 with 3-small. Cost is negligible for any option; the deciding factors are quality, operational simplicity and the chat provider already being OpenAI.

**Decision.** `text-embedding-3-small`: good quality, one provider and one API key for chat and embeddings, and 1536 dimensions fit an HNSW index directly.

**Consequences.**
- The model name and dimension are configuration (`spring.ai.openai.embedding.options.model`, `spring.ai.vectorstore.pgvector.dimensions`). Changing either requires a **full re-index** (`POST /api/ai/reindex`) and, for the dimension, a migration of the `embedding` column.
- 3-small produces fairly low absolute cosine similarities even for good matches. The initial threshold (0.45, requirements §3.1) is a starting point and **must be calibrated** on the golden dataset (`evaluation-strategy.md`).
- Privacy trade-off accepted for the assessment: ticket text is sent to OpenAI. Switching to Ollama is a starter + config change + re-index.

### ADR-3 Vector store

| Option | Pros | Cons |
|---|---|---|
| **pgvector in the existing PostgreSQL** ✅ | One database, one backup; metadata filters in SQL (JSONPath); vectors survive restart with the tickets (NFR-2); already installed (0.8.1) | Not a dedicated vector DB; fine at this scale |
| Chroma | Simple dev API | Extra service/container to run and keep in sync; second persistence story |

**Decision.** pgvector with an HNSW index and cosine distance. The `vector_store` table is created by **Flyway**, not by Spring AI (`spring.ai.vectorstore.pgvector.initialize-schema=false`). Flyway keeps every table, index and dimension in one versioned place. Spring AI's schema initialisation would pick the table definition itself and try to create extensions at startup, but the app role should need no extension privileges (pgvector is not a trusted extension; see CLAUDE.md).

### ADR-4 Re-ingestion trigger (FR-16, AC-20, OQ-12)

**Options.** (a) Call the indexer directly inside the service transaction. (b) Domain event + `@TransactionalEventListener(AFTER_COMMIT)`, synchronous. (c) Same, but `@Async`. (d) Scheduled polling of `updated_at`.

**Decision.** (b). Every write method in `TicketService` publishes `TicketChangedEvent(ticketKey)`. `TicketIndexer` handles it **after commit**, on the request thread:
- The vector store never sees uncommitted data, and a rolled-back write never triggers re-indexing.
- The slow embedding call does not hold the ticket transaction open.
- The response returns after re-indexing, so the chunks are fresh as soon as the client sees 2xx. Tests need no polling.
- Re-indexing replaces **all** chunks of the ticket: delete by filter `ticketId == '<key>'`, then add. Both run in one `REQUIRES_NEW` transaction, so a failure leaves the old chunks in place (stale but present) rather than none.
- **Pitfall to respect:** in the AFTER_COMMIT phase the original transaction has already committed; data access there must run in a new transaction (`Propagation.REQUIRES_NEW`), or writes are silently not committed. Spring 6.1+ also rejects a plain `@Transactional` on a `@TransactionalEventListener` method.
- Failures (e.g. OpenAI down) are caught and logged with the ticket key; they never fail the user's request (OQ-12). `POST /api/ai/reindex` repairs everything.

Rejected: (a) would put a network call inside the DB transaction and index data that might roll back. (c) adds thread-pool config and makes AC-20 tests wait. (d) adds latency and a scheduler for no benefit.

### ADR-5 Prompt structure and prompt caching (NFR-8, NFR-13)

- One call per question: `[system: static instructions] [user: delimited context + question]`. No chat memory (OS-5), no tools (NFR-7).
- The **system message is byte-for-byte identical on every request** and comes first. It contains the rules, the JSON output contract and two short worked examples (one grounded, one "not answerable").
- **Caching.** OpenAI prompt caching is automatic (no opt-in) and only applies once the prompt is at least ~1,024 tokens; the cached part is the longest matching prefix. The static system prompt is therefore sized to **≥ 1,024 tokens** by the worked examples, which also improve answer format. Cache hits are observable in the Chat Completions usage field `prompt_tokens_details.cached_tokens`, which the app logs at DEBUG per request. (OpenAI's guide, checked 2026-09-25, now documents GPT-5.6+ behaviour and says the minimum "varies" for earlier models; the ≥ 1,024-token sizing is the conservative choice.)
- Ticket text is wrapped in `<ticket id="TKT-…">…</ticket>` blocks. The system prompt says the content of these blocks is data, and instructions inside them must be ignored (NFR-13).

### ADR-6 Chat model

`gpt-4.1-mini` (checked available on the project key). It is cheap, good at following instructions and JSON output, and accepts `temperature: 0`. The GPT-5 family was rejected: those reasoning models do not accept a custom `temperature`, and Spring AI sends one by default, which fails the request. Configurable via `spring.ai.openai.chat.options.model`.

Cost per question ≈ 1.1 k static + ~1.5 k context tokens in, ~200 out ≈ $0.001–0.002.

### ADR-7 Query analysis without an LLM (OQ-4, OQ-5, NFR-7)

A deterministic `QueryAnalyzer` turns explicit constraints in the question into a vector-store filter before retrieval:

| Rule | Trigger (case-insensitive) | Filter | Threshold |
|---|---|---|---|
| R1 ticket key | `TKT-\d+` | `ticketId in [keys]` | **not applied**: an explicit key is an exact lookup; similarity between "What was the resolution for TKT-1001?" and the ticket text can legitimately be low |
| R2 priority | "high priority", "high-priority", "urgent", "critical" | `priority in ['HIGH','CRITICAL']` | applied |
| R3 resolved | "resolved", "fixed", "closed" | `status in ['RESOLVED','CLOSED']` | applied |

Rules combine with AND. An unknown key under R1 returns no chunks, so the no-match response follows (FR-21a). No LLM call is used to extract filters.

### ADR-8 Grounding enforcement in depth (FR-19..FR-21)

1. **Threshold gate.** If no chunk passes, return the no-match response without calling the LLM.
2. **Prompt.** Answer only from the `<ticket>` blocks. If they don't answer the question, return `answerable: false`.
3. **Structured output.** The model returns JSON `{answerable, answer, citedTicketIds}`. It is parsed strictly; unparseable output → no-match (logged).
4. **Server-side citation check.** Cited IDs not in the retrieved set are dropped and logged as warnings. If no valid citation remains, the response becomes no-match (a grounded answer must cite ≥ 1 retrieved ticket, FR-20).
5. **Low randomness.** `temperature: 0`.

### ADR-10 Context expansion: retrieve chunks, answer from tickets

**Context.** The evaluation found an answer claiming TKT-1003 had "no detailed resolution" (M-11): top-K held only its COMMENTS chunk, so the LLM never saw the resolution in its SUMMARY chunk.

**Decision.** Similarity search stays chunk-based (precise matching), but the LLM context is ticket-based: for every retrieved ticket, its SUMMARY chunk(s) are loaded by metadata (`SummaryChunkLookup`, plain SQL on `vector_store`, no second embedding call) and placed before its matched chunks. `retrieval.matches` still reports only true similarity matches. The prompt also forbids inferring absence from partial context.

**Consequences.** Slightly larger prompts (≤ top-K extra short chunks); no extra provider calls; negative claims about tickets are no longer produced from partial context.

### ADR-9 Test database

Integration tests run against a dedicated local database `tickets_test` on the same PostgreSQL 15 + pgvector instance, not Testcontainers. Docker is not running on the development machine, and the local instance is the same version and extension as runtime. Each test class starts from a truncated schema. This supersedes the Testcontainers line in `.claude/rules/testing.md`, which has been updated.

## 7. Configuration (NFR-4)

| Key | Default | Notes |
|---|---|---|
| `app.rag.top-k` | 5 | 1–20 |
| `app.rag.similarity-threshold` | 0.40 | 0–1, cosine **similarity** (not distance). Calibrated in Step 5 (was 0.45). |
| `app.rag.chunk.max-tokens` | 400 | comment packing size |
| `app.seed.enabled` | false (`true` in the `dev` profile) | load `seed/tickets.json` if the ticket table is empty |
| `spring.ai.openai.api-key` | `${OPENAI_API_KEY}` | env only (NFR-6) |
| `spring.ai.openai.chat.options.model` | `gpt-4.1-mini` | |
| `spring.ai.openai.chat.options.temperature` | 0 | |
| `spring.ai.openai.embedding.options.model` | `text-embedding-3-small` | |
| `spring.ai.vectorstore.pgvector.dimensions` | 1536 | must match the model |
| `spring.ai.vectorstore.pgvector.index-type` / `distance-type` | `HNSW` / `COSINE_DISTANCE` | |
| `spring.ai.vectorstore.pgvector.initialize-schema` | false | Flyway owns the table |
| `spring.datasource.*` | `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` | env only |

Every `app.rag.*` value can be overridden by an environment variable (e.g. `APP_RAG_TOP_K=8`) without a code change (AC-21).

## 8. Error handling and resilience

- `GlobalExceptionHandler` maps exceptions to RFC 7807 ProblemDetail (catalogue in `api-contract.md`).
- AI provider failure during `/ask` → 503 `ai-unavailable`. Ticket CRUD is unaffected (NFR-11).
- AI provider failure during re-indexing → logged, request still succeeds, recovery via `/api/ai/reindex` (ADR-4).

## 9. Security

- Secrets only from environment variables; `.env` is git-ignored (NFR-6).
- No auth (OS-1). CORS is not needed in dev: the Vite dev server proxies `/api` to the backend.
- Prompt injection: delimited context + explicit instruction + injection case in the golden set (NFR-13).
- Logs never contain the API key. Full prompts are logged only at DEBUG.

## 10. Risks

| Risk | Mitigation |
|---|---|
| Threshold too high for 3-small → false "no relevant tickets" | Calibrate on the golden set in Step 5; the value is configuration |
| LLM cites a ticket it wasn't given | Server-side citation check (ADR-8) |
| Spring AI API drift between versions | Versions pinned; `/review-code` flags unverified APIs; compile + tests confirm |
| Stale chunks after a failed re-index | Old chunks kept (ADR-4) + reindex endpoint |
