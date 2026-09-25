# AI-Powered Support Ticket Management System

Support tickets with an enforced status workflow, plus a question-answering assistant that answers **only from ticket history**, cites the tickets it used, and says so when nothing relevant exists.

**Stack:** Java 21 · Spring Boot 3.5 · Spring AI 1.1 · PostgreSQL 15 + pgvector · OpenAI (`text-embedding-3-small`, `gpt-4.1-mini`) · React + Vite + TypeScript

## How this was built (spec-driven, with AI)

| Step | Artefacts |
|---|---|
| 0. Reusable AI instructions | [`CLAUDE.md`](CLAUDE.md), [`.claude/rules/`](.claude/rules) (Spring Boot, testing, API, RAG), [`.claude/commands/`](.claude/commands) (`/review-code`, `/review-spec`, `/generate-tests`, `/review-rag-output`), [`.claude/skills/documentation`](.claude/skills/documentation), prompt-logging hook |
| 1. Requirements | [`spec/requirements.md`](spec/requirements.md) — FR/NFR/AC IDs, state machine matrix, decided open questions |
| 2. Specification | [`spec/`](spec) — architecture (decision records incl. chunking & embedding model), data model, state machine, API + RAG contracts, ingestion, evaluation, UI flow, test strategy |
| 3. Plan | [`spec/tasks.md`](spec/tasks.md) |
| 4–6. Implement → Test → Review → Fix | [`backend/`](backend), [`frontend/`](frontend), 119 backend + 8 frontend tests |
| RAG evaluation | [`docs/rag-evaluation-report.md`](docs/rag-evaluation-report.md) — golden set, threshold calibration, grounding audit |
| AI mistakes caught | [`docs/ai-mistakes-log.md`](docs/ai-mistakes-log.md) — 12 entries: wrong code, hallucinated APIs, ungrounded RAG answers |
| Prompt history | [`docs/prompt-history.md`](docs/prompt-history.md): each prompt recorded automatically by a `UserPromptSubmit` hook |
| Prompt log (actual) | [`.specstory/history/`](.specstory/history) (complete, verbatim, one file per session) recorded automatically by a `UserPromptSubmit` hook |
| Prompt playbook | [`docs/prompt-playbook.md`](docs/prompt-playbook.md): the recommended spec-driven prompt sequence for this project, plus lessons learned |
| Code knowledge graph | [`graphify-out/GRAPH_REPORT.md`](graphify-out/GRAPH_REPORT.md), `graphify-out/graph.html` (open locally in a browser) |
| Token optimisation | [`docs/token-optimisation.md`](docs/token-optimisation.md) |

The git history shows the order: steering files → requirements → specs → plan → code.

## Run it

Prerequisites: JDK 21, Maven, Node 20+, PostgreSQL 15+ with the pgvector extension, an OpenAI API key.

```bash
# 1. Database (once). pgvector is not a trusted extension, so a superuser creates it.
createuser -P tickets
createdb -O tickets tickets
psql -d tickets -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 2. Secrets: copy and fill in (the file is git-ignored)
cp .env.example .env

# 3. Backend on :8080 — the dev profile seeds 30 demo tickets and indexes them (~2 min, first run only)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Frontend on :5173 (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

Swagger UI: http://localhost:8080/swagger-ui.html

## Tests

```bash
# Integration tests use a separate database
createdb -O tickets tickets_test && psql -d tickets_test -c "CREATE EXTENSION IF NOT EXISTS vector;"

cd backend && mvn test              # 119 tests, no OpenAI calls (deterministic fake embedding model)
cd backend && mvn -Prag-eval verify # RAG evaluation against real OpenAI (~4 min, ~$0.05)
cd frontend && npm test             # 8 UI tests
```

## Key API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/tickets` | create (status `OPEN`) |
| `GET` | `/api/tickets?q=&status=&page=&size=` | list, keyword search, status filter |
| `GET` / `PATCH` | `/api/tickets/{key}` | details / update title, description, priority, category, assignee, resolution notes |
| `POST` | `/api/tickets/{key}/transitions` | `{ "targetStatus": "IN_PROGRESS" }` — invalid transitions → 409 |
| `POST` | `/api/tickets/{key}/comments` | add comment |
| `POST` | `/api/ai/ask` | `{ "question": "…" }` → `grounded`, `answer`, `citations[]`, `noMatchReason`, `retrieval` |
| `POST` | `/api/ai/reindex` | rebuild all embeddings |

State machine: `OPEN → IN_PROGRESS → RESOLVED → CLOSED`, `OPEN → CANCELLED`, `IN_PROGRESS → CANCELLED`. Everything else is rejected.

## Configuration

| Property / env var | Default |
|---|---|
| `app.rag.top-k` / `APP_RAG_TOP_K` | 5 |
| `app.rag.similarity-threshold` / `APP_RAG_SIMILARITY_THRESHOLD` | 0.40 (calibrated) |
| `spring.ai.openai.chat.options.model` | `gpt-4.1-mini` |
| `spring.ai.openai.embedding.options.model` | `text-embedding-3-small` |
| `OPENAI_API_KEY`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | from `.env` / environment only |
