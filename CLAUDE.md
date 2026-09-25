# Support Ticket Management System — AI Steering

AI-powered support ticket system: Java 21 · Spring Boot 3 · Spring AI · PostgreSQL + pgvector · OpenAI · React (Vite + TS).

## Workflow (mandatory)
Requirement → Specification → Plan/Tasks → Implementation → Testing → Review → Fix

- Never implement anything that is not described in `spec/`. If the spec is missing or ambiguous, stop and update the spec first.
- Work one task from `spec/tasks.md` at a time. Tick it off only after its tests pass.
- Every AI mistake caught (wrong code, hallucinated API, ungrounded RAG answer) is recorded in `docs/ai-mistakes-log.md`.

## Rules (always apply)
- @.claude/rules/java-springboot.md
- @.claude/rules/api-standards.md
- @.claude/rules/testing.md
- @.claude/rules/rag-vector-store.md

## Commands
- `/review-spec` — review a spec file for gaps and contradictions
- `/review-code` — review a diff against the rules
- `/generate-tests` — generate tests for a class/feature from the spec
- `/review-rag-output` — audit an `/api/ai/ask` answer for hallucination / ungrounded claims

## Layout
```
backend/    Spring Boot app (Maven)
frontend/   React + Vite + TS
spec/       specifications (source of truth)
docs/       prompt history, AI mistakes log, ADRs
```

## Local environment
- JDK 21 is installed via Homebrew but is not the system default (that is Corretto 17). Prefix every Maven command:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn ...`
- PostgreSQL 15 (Homebrew) runs on localhost:5432; the pgvector extension (0.8.1) is already available.
- pgvector is not a trusted extension, so the app role `tickets` cannot create it. It is created once by a superuser during setup; migrations must only use `CREATE EXTENSION IF NOT EXISTS vector`.

## Hard constraints
- No secrets in the repo. `OPENAI_API_KEY`, DB password come from environment variables only.
- The assistant is a single retrieve → generate flow. No agents, no tool calling, no side effects.
- Ticket state machine is enforced in the domain layer, never only in the UI.
