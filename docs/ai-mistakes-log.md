# AI Mistakes Log

Mistakes or incorrect suggestions made by the AI assistant that were caught during development —
wrong code **and** ungrounded / hallucinated assistant answers. Format: see `.claude/skills/documentation/SKILL.md`.

<!-- Add entries as M-1, M-2, ... Only record real, verified mistakes with evidence. -->

## M-1: Contradictory citation requirement in requirements spec
- Phase: spec
- What the AI produced: in `spec/requirements.md` rev 1, FR-20 said "Every answer cites the ticket key(s) it was built from", and AC-17's pass condition said "Every answer lists ≥1 cited ticket key". The same draft required the no-match response (FR-21 / AC-18) to "cite no tickets".
- Why it was wrong: both conditions can't hold together. A no-match response is also an answer, so any implementation fails either AC-17 or AC-18. A test written from AC-17 would also push the model to produce a citation for out-of-scope questions, which is exactly the fabrication the PDF forbids.
- How it was caught: `/review-spec spec/requirements.md` (Contradictions check), before any design or code was written.
- Fix: FR-20 / AC-17 now apply only to `grounded: true` responses. The glossary defines the no-match response as `grounded: false` with an empty citation list (rev 2).
- Prevention: when writing "every X" requirements, check them against the no-match/empty case. `/review-spec` stays mandatory before a spec moves from Draft to Reviewed.
