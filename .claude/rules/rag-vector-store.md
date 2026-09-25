# RAG / Vector Store Guidelines

Rationale and trade-offs live in `spec/architecture.md` and `spec/rag-ingestion.md`. This file is the enforced convention.

## Knowledge documents
- Source: ticket title, description, resolution notes, comments.
- One ticket → one or more `Document`s. Every chunk carries metadata:
  `ticketId` (TKT-key), `status`, `priority`, `assignee`, `category`, `chunkType` (`SUMMARY` | `COMMENT`), `updatedAt`.
- Chunk text is prefixed with a short header (`[TKT-1001 | PAYMENT | HIGH | RESOLVED] Title`) so each chunk is self-describing when retrieved alone.

## Chunking convention
- Structure-aware, not fixed-size: `SUMMARY` chunk = title + description + resolution; comments are packed in time order into `COMMENTS` chunks of ≤ 400 tokens, breaking only between comments.
- Only split a section with `TokenTextSplitter` if it exceeds ~500 tokens; repeat the header on every piece.
- Deterministic chunk IDs: `UUID.nameUUIDFromBytes("<ticketId>#<chunkType>#<index>")` (PgVectorStore ids are UUIDs).
- Metadata values are never `null` (Spring AI `Document` rejects them): unassigned → `"unassigned"`.

## Embeddings & store
- Embedding model: OpenAI `text-embedding-3-small` (1536 dims). Changing model ⇒ full re-index; dimension is set in config, not code.
- Store: pgvector, HNSW index, cosine distance, table `vector_store`.

## Freshness
- On ticket create/update/comment/status change: delete all chunks where `ticketId == X`, then re-add. Triggered via `@TransactionalEventListener(phase = AFTER_COMMIT)` so the vector store never contains uncommitted data.
- The re-index work runs in `Propagation.REQUIRES_NEW` — in AFTER_COMMIT the original transaction is already committed, so writes without a new transaction are silently lost. Synchronous (no `@Async`), failures logged, never rethrown.
- A startup/admin re-index (`POST /api/ai/reindex`) exists for recovery.

## Retrieval defaults (configurable, never hardcoded)
```yaml
app.rag.top-k: 5
app.rag.similarity-threshold: 0.45
```
- Optional metadata filter (e.g. `priority == 'HIGH'`) only when the question explicitly asks for it.

## Generation & grounding
- System prompt is static and placed first (enables OpenAI automatic prompt caching of the prefix). Dynamic context and question come after.
- The model may only use the supplied context. If context is empty → do **not** call the LLM; return the fixed "no relevant tickets found" response.
- Model must cite ticket IDs; server post-validates that every cited ID was in the retrieved set and drops/flags any that weren't.
- `temperature: 0` (or ≤ 0.2).
- No tool calling, no agents, no follow-up actions.
