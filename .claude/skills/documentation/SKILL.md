---
name: documentation
description: Write or update project documentation (spec files, architecture decisions, README, API docs, AI mistakes log). Use whenever a spec/*.md, docs/*.md, README or ADR is created or changed.
---
# Documentation Skill

## Principles
- Specs are the source of truth and are written **before** code. Code that disagrees with a spec is a bug in one of them — fix whichever is wrong and note it.
- Every decision records: context, options considered, choice, consequences (ADR style). Put ADRs in `spec/architecture.md` under "Decisions".
- Prefer tables and Mermaid diagrams over prose for data models, state machines and flows.
- Every requirement gets an ID (`FR-1`, `NFR-3`, `AC-12`) so tests and tasks can reference it.

## Templates
**Spec file header**
```
# <Title>
Status: Draft | Reviewed | Implemented
Traces to: FR-x, AC-y
```

**AI mistake log entry** (`docs/ai-mistakes-log.md`)
```
## M-<n>: <short title>
- Phase: spec | code | test | RAG answer
- What the AI produced: (quote / snippet)
- Why it was wrong: (evidence — compile error, failing test, docs link, audit)
- How it was caught: (review command, test, manual reading)
- Fix: (what we changed)
- Prevention: (rule/steering file updated?)
```

## Checklist before finishing a doc
- [ ] IDs are consistent with `spec/requirements.md`
- [ ] No placeholder text / TODO left unowned
- [ ] Anything configurable is listed with its default
- [ ] Diagrams render (Mermaid syntax valid)
