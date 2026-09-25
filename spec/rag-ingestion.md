# RAG Ingestion
Status: Reviewed
Traces to: FR-14, FR-15, FR-16, AC-15, AC-20, OQ-12, OQ-13, ADR-1, ADR-4

## 1. When a ticket is (re-)indexed

| Trigger | Path |
|---|---|
| Ticket created, updated (PATCH), commented, transitioned (incl. closed/cancelled) | `TicketService` publishes `TicketChangedEvent(key)` → `TicketIndexer.onTicketChanged` after commit (ADR-4) |
| Seed load | `SeedDataLoader` creates tickets through `TicketService`, so the same event path indexes them |
| Recovery / model change | `POST /api/ai/reindex` → `TicketIndexer.reindex(key)` for every ticket |

All statuses are indexed, including `CANCELLED` (OQ-13).

## 2. Chunk construction (`TicketChunker`, ADR-1)

Pure function: `List<Document> chunk(Ticket ticket, List<Comment> comments)`. No I/O, fully unit-testable.

**Header** (first line of every chunk):
```
[TKT-1001 | PAYMENT | HIGH | RESOLVED | assignee: Priya Sharma] Card payments failing at checkout
```
Unassigned → `assignee: unassigned`.

**SUMMARY chunk** (always exactly one logical chunk):
```
[TKT-1001 | PAYMENT | HIGH | RESOLVED | assignee: Priya Sharma] Card payments failing at checkout
Description: Customers get "payment declined" for Visa cards since 09:00. Mastercard works.
Resolution: The payment gateway API key had expired at midnight. Rotated the key and added expiry monitoring.
```
The `Resolution:` line is omitted when there are no resolution notes.

**COMMENTS chunk(s)** (only when the ticket has comments):
```
[TKT-1001 | PAYMENT | HIGH | RESOLVED | assignee: Priya Sharma] Card payments failing at checkout
Comments:
- Priya Sharma (2026-09-25): Gateway returns decline code 05 for all Visa transactions.
- Ravi Kumar (2026-09-25): Gateway status page shows no incident.
```
Comments are added oldest first. A new COMMENTS chunk starts when adding the next comment would exceed `app.rag.chunk.max-tokens` (default 400), so chunks only break **between** comments.

**Size fallback.** If a SUMMARY body, or a single comment, exceeds ~500 tokens on its own, the body text is split with Spring AI `TokenTextSplitter` (chunk size 500) and the header is prepended to each piece.

**Token estimate.** `ceil(characters / 4)`. Deterministic and good enough for packing decisions; the embedding model's input limit (8k tokens) is far above any chunk.

**IDs and metadata.** `id = UUID.nameUUIDFromBytes("<key>#<chunkType>#<index>")`. Metadata per `data-model.md` §3 (`ticketId`, `status`, `priority`, `assignee`, `category`, `title`, `chunkType`, `chunkIndex`, `updatedAt`). No metadata value is ever `null`.

## 3. Indexing algorithm (`TicketIndexer.reindex(key)`)

Runs in a `Propagation.REQUIRES_NEW` transaction (ADR-4):

1. Load the ticket and its comments by key. If it does not exist, log a warning and return.
2. `docs = chunker.chunk(ticket, comments)`.
3. `vectorStore.delete(filter: ticketId == key)` — removes **all** previous chunks, including ones whose index no longer exists (e.g. comments re-packed).
4. `vectorStore.add(docs)` — embeds via OpenAI and inserts.
5. Commit. Log `ticket=<key> chunks=<n> ms=<t>` at INFO.

If step 4 fails (embedding error), the exception rolls back the transaction, so the delete in step 3 is undone: the old chunks remain (stale but present). `onTicketChanged` catches the exception, logs `reindex failed ticket=<key>` at WARN, and does not rethrow — the user's request still succeeds (OQ-12).

## 4. Re-index all — `POST /api/ai/reindex`

Iterates all tickets (pages of 50) and calls `reindex(key)` for each. Response `200 { "tickets": 30, "chunks": 52, "failed": [] }`. `failed` lists keys whose re-index failed. Contract in `rag-api-contract.md`.

## 5. Seed data (`SeedDataLoader`)

- Runs on `ApplicationReadyEvent` when `app.seed.enabled=true` **and** the ticket table is empty.
- Reads `classpath:seed/tickets.json`: an array of `{ title, description, priority, category, assignee, targetStatus, resolutionNotes, comments: [{ author, body }] }`.
- For each entry, in file order: create → add comments → PATCH resolution notes (if any) → apply the allowed transition path to `targetStatus` (e.g. OPEN→IN_PROGRESS→RESOLVED→CLOSED). Using the real service means seed data obeys the state machine and is indexed by the normal path.
- On an empty database the keys are deterministic: the first entry is `TKT-1001`, and so on. `evaluation-strategy.md` relies on this.

## 6. Failure modes

| Failure | Effect | Recovery |
|---|---|---|
| OpenAI embeddings down during a ticket write | Ticket saved; chunks stale; WARN log | `POST /api/ai/reindex` |
| OpenAI down during seed load | Tickets seeded; some unindexed | `POST /api/ai/reindex` |
| App crash between commit and re-index | Chunks stale | `POST /api/ai/reindex` |
| Embedding model/dimension changed | Old vectors incompatible | Migration for the column dimension + `POST /api/ai/reindex` |

## 7. Verification (AC-15, AC-20)

- Unit (`TicketChunkerTest`): header format, SUMMARY with/without resolution, comment packing at the 400-token boundary, oversize split repeats the header, deterministic IDs, no null metadata.
- Integration with a deterministic fake `EmbeddingModel` (no OpenAI): create a ticket → chunks exist with all metadata (AC-15). Update title / add a comment / transition → immediately after the 2xx response, the chunks for that `ticketId` contain the new text and status and none of the replaced text (AC-20). A failing fake embedding model → the request still returns 2xx and the old chunks remain.
