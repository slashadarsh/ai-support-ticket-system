---
description: Generate tests for a class or feature from the spec (not from the implementation)
argument-hint: <class, feature or spec section>
---
Generate tests for: $ARGUMENTS

1. First read the relevant spec (`spec/test-strategy.md` plus the feature spec). Derive test cases **from the spec's acceptance criteria**, not from the current implementation — list them as a table (id, scenario, expected) before writing code.
2. Follow `.claude/rules/testing.md` (layer choice, naming, no real OpenAI in non-eval tests).
3. Include negative and boundary cases: blank/oversized fields, unknown ticket key, every illegal status transition, empty retrieval result.
4. For RAG: assert on `grounded`, cited `ticketId`s ⊆ retrieved set, and the no-match message — never on exact LLM wording.
5. After writing, run the tests. For each test, state whether it would still pass if the code under test were deleted/stubbed — rewrite any that would.
