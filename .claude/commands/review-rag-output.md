---
description: Audit an AI assistant answer for hallucination / ungrounded claims
argument-hint: <question> (or paste a full /api/ai/ask request+response JSON)
---
Input: $ARGUMENTS

If only a question is given, call `POST http://localhost:8080/api/ai/ask` with it and also fetch the retrieved chunks (response `sources[]` / debug field).

Audit the answer **only against the retrieved ticket context**, not your own knowledge:

1. Split the answer into atomic claims. For each claim output:
   | Claim | Supported by (ticketId + quoted snippet) | Verdict: SUPPORTED / PARTIAL / UNSUPPORTED / CONTRADICTED |
2. **Citation check** — every cited ticket ID exists in the retrieved set; every retrieved ticket used in the answer is cited.
3. **General-knowledge leakage** — any advice/explanation not traceable to a ticket (e.g. generic "check your payment gateway API keys").
4. **No-match honesty** — if retrieval was empty or all scores < threshold, the answer must be the explicit no-relevant-tickets response.
5. **Scope** — did the answer suggest or claim to take actions (create ticket, notify)? That's a violation.

Verdict: GROUNDED / PARTIALLY GROUNDED / HALLUCINATED, with a one-line reason.
If not GROUNDED, draft an entry for `docs/ai-mistakes-log.md` (question, answer excerpt, what was wrong, root cause hypothesis: retrieval vs prompt vs threshold).
