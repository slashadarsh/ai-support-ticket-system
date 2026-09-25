---
description: Review a spec file for gaps, ambiguity and contradictions
argument-hint: <spec file, e.g. spec/api-contract.md>
---
Review `$ARGUMENTS` against `spec/requirements.md` and the other files in `spec/`.

Report:
1. **Coverage** — requirements / acceptance criteria not addressed by this spec (quote the requirement).
2. **Ambiguity** — statements two engineers could implement differently. Propose a precise rewrite.
3. **Contradictions** — conflicts with another spec file (cite both).
4. **Untestable statements** — anything without a verifiable acceptance criterion.
5. **Scope creep** — anything not asked for (e.g. agentic behaviour, auth) that should be cut or marked out-of-scope.
6. **Assumptions** — list implicit assumptions that should be made explicit.

Do not edit the file. Output a prioritised list of proposed changes.
