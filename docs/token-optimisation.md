# Token Optimisation

Two separate concerns: tokens spent by the **AI coding assistant** while building the project, and tokens spent by the **application's own assistant** at runtime.

## 1. While building (Claude Code)

| Technique | How it was applied here |
|---|---|
| Reusable, cached context | `CLAUDE.md` + `.claude/rules/*.md` hold the stable instructions (stack, conventions, RAG rules). They are loaded once per session and form a static prompt prefix that Claude Code's prompt caching reuses on every turn, instead of repeating conventions in each prompt. |
| Specs as the context boundary | Each task in `spec/tasks.md` names the spec sections it needs, so the assistant reads those files instead of scanning the whole repo. |
| Targeted evidence instead of dumps | API questions were answered with `javap` on one class in one jar (e.g. `TokenTextSplitter`, `SpringAiRetryProperties`), a Maven Central metadata query for versions, and one docs page for prompt caching, rather than pasting library sources or long docs. |
| Fresh sessions per phase | Requirements were written in a separate session; project knowledge lives in files, so a new session starts small instead of carrying a long chat history. |
| Deterministic tests without an LLM | `FakeEmbeddingModel` and a mocked `ChatModel` keep 119 tests at zero API tokens; only the explicit `-Prag-eval` run calls OpenAI. |
| Code knowledge graph (graphify) | `/graphify .` built [`graphify-out/`](../graphify-out/GRAPH_REPORT.md) from the code with AST parsing only (**0 LLM tokens**): 679 nodes, 1,783 edges, 30 labelled communities, no import cycles. Questions such as "what does `AskService` depend on?" can then be answered with `graphify query` / `graphify path` over the graph instead of re-reading source files. Built after the implementation (not used during it); docs were not added to the graph (code-only run). |

## 2. At runtime (the `/api/ai/ask` assistant)

| Technique | Implementation | Evidence |
|---|---|---|
| **Prompt caching of the static prefix** | The system prompt (`prompts/ask-system.st`: rules, guardrails, JSON contract, two worked examples) is byte-identical on every request and is the first message; the dynamic ticket context and question come after it (ADR-5). It is sized to ≥ 1,024 tokens, OpenAI's minimum for automatic caching. `PromptFactoryTest` guards both properties. | App log, same question asked twice: `promptTokens=1910 cachedTokens=0` then `promptTokens=1910 cachedTokens=1664`, i.e. **87 % of the prompt served from cache** (cached input is billed at a discount and processed faster). |
| No LLM call when nothing is relevant | The similarity threshold gate returns the fixed no-match response without calling the chat model (FR-21a). | `AskApiIT.noRelevantTicketsNeverCallsTheLlm`; eval N1–N6 all `NO_RELEVANT_TICKETS`. |
| No LLM for query analysis | Priority/status/ticket-key constraints are extracted with deterministic rules (ADR-7), not an extra LLM call. | `QueryAnalyzerTest` |
| Small, self-describing chunks | Structure-aware chunking (ADR-1): 1–2 chunks per ticket (42 chunks for 30 tickets), so top-K = 5 context stays around 1–1.5 k tokens. | `docs/rag-evaluation-report.md` |
| Context expansion without a second embedding | Summary chunks of retrieved tickets are fetched by metadata SQL, not by another similarity search (ADR-10). | `SummaryChunkLookup` |
| Cheap models where quality allows | `text-embedding-3-small` (≈ $0.02 / 1M tokens) and `gpt-4.1-mini`; a full re-index of the demo data costs ≈ $0.0003, one question ≈ $0.001–0.002. | ADR-2, ADR-6 |
| Bounded retries | Provider retries capped at 2 attempts (M-5), so failures don't multiply token spend or latency. | `ProviderRetryIT` |
