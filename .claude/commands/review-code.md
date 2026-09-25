---
description: Review changed code against project rules and the spec
argument-hint: [path or git ref, default = uncommitted diff]
---
Review the code in: $ARGUMENTS (if empty, use `git diff HEAD`).

Check against `.claude/rules/*.md` and the relevant file in `spec/`. Report findings as a table:
| # | Severity (BLOCKER/MAJOR/MINOR) | File:line | Issue | Rule/spec violated | Suggested fix |

Specifically check:
1. Correctness vs spec — does the behaviour match `spec/api-contract.md`, `spec/state-machine.md`, `spec/rag-api-contract.md`?
2. State machine enforced in the domain layer, not bypassable via PATCH.
3. Validation on every request DTO; errors mapped to ProblemDetail with the right status.
4. **Hallucinated APIs** — any Spring / Spring AI class, method, or property you are not certain exists. Flag with "VERIFY" and say how to verify (docs URL, IDE autocomplete, compile).
5. Secrets, logging of sensitive data, SQL injection, N+1 queries.
6. Missing tests for new behaviour.

Do not fix anything. End with: "Items worth recording in docs/ai-mistakes-log.md:" listing any issue that originated from earlier AI-generated code.
