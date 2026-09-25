# RAG Evaluation Report

Method: `spec/evaluation-strategy.md`. Real models (`text-embedding-3-small`, `gpt-4.1-mini`, temperature 0), 30-ticket golden dataset seeded through the real service into `tickets_test`, 16 golden questions asked through `POST /api/ai/ask`. Run with `mvn -Prag-eval verify` (`RagEvaluationIT`). Date: 2026-09-25.

## 1. Threshold calibration (retrieval only, top-K = 5)

| threshold | hit@K (G1–G9) | out-of-scope questions retrieving ≥ 1 chunk (of 6) |
|---|---|---|
| 0.20 | 1.00 | 1 |
| 0.25 | 1.00 | 1 |
| 0.30 | 1.00 | 1 |
| **0.35** | 1.00 | **0** |
| **0.40** ← chosen | 1.00 | 0 |
| 0.45 (initial) | 1.00 | 0 |
| **0.50** | 1.00 | 0 |
| 0.55 | 0.33 | 0 |
| 0.60 | 0.11 | 0 |

- `text-embedding-3-small` similarities are low in absolute terms: good matches score **0.45–0.56**, the exact-key lookup "What was the resolution for ticket TKT-1001?" scores only **0.35** against its own ticket. That low score is why ticket-key questions bypass the threshold (ADR-7, R1).
- Safe band (full recall and no out-of-scope leakage): **0.35–0.50**. Recall collapses just above it (0.55 → 0.33).
- **Chosen: 0.40**, with margin on both sides and room for secondary relevant tickets (e.g. TKT-1002/1003 at ~0.46 for G1). The spec's original rule would have picked 0.50, the cliff edge (M-12).

## 2. Results

**Baseline** (threshold 0.45, first prompt version): all automated metrics passed (hit@K 1.00, grounded rate 1.00, citation precision 1.00, filter correctness 1.00, no-match accuracy 1.00, 0 injections followed). The claim-level audit still found two wrong answers the metrics could not see (§3).

**Final** (threshold 0.40, prompt fixes M-9/M-10, context expansion M-11):

| ID | Question | grounded | reason | cited | retrieved (score) | verdict |
|---|---|---|---|---|---|---|
| G1 | Have we seen payment failures before? | true |  | [TKT-1001, TKT-1003, TKT-1002] | [TKT-1001 (0.501), TKT-1003 (0.459), TKT-1002 (0.457)] | PASS |
| G2 | What was the resolution for ticket TKT-1001? | true |  | [TKT-1001] | [TKT-1001 (0.352)] | PASS |
| G3 | What are the common causes of shipment tracking issues? | true |  | [TKT-1007, TKT-1011, TKT-1008] | [TKT-1007 (0.555), TKT-1011 (0.555), TKT-1008 (0.514)] | PASS |
| G4 | Show me similar resolved tickets about tracking numbers not updating. | true |  | [TKT-1007] | [TKT-1007 (0.511), TKT-1010 (0.501), TKT-1008 (0.495), TKT-1009 (0.468)] | PASS |
| G5 | Which high-priority tickets are related to payment? | true |  | [TKT-1001, TKT-1005, TKT-1002] | [TKT-1001 (0.551), TKT-1005 (0.517), TKT-1002 (0.495)] | PASS |
| G6 | Why were users not receiving password reset emails? | true |  | [TKT-1013] | [TKT-1013 (0.544)] | PASS |
| G7 | Has the Android app ever crashed on startup? | true |  | [TKT-1018] | [TKT-1018 (0.531)] | PASS |
| G8 | Were customers ever charged twice for one order? | true |  | [TKT-1003] | [TKT-1003 (0.536)] | PASS |
| G9 | Is there an issue with the warehouse printer? | true |  | [TKT-1028] | [TKT-1028 (0.539)] | PASS |
| A1 | Show me similar resolved tickets. | false | NO_RELEVANT_TICKETS | [] | [] | PASS |
| N1 | What is the capital of France? | false | NO_RELEVANT_TICKETS | [] | [] | PASS |
| N2 | How do I configure a Kubernetes ingress controller? | false | NO_RELEVANT_TICKETS | [] | [] | PASS |
| N3 | What was the resolution for ticket TKT-9999? | false | NO_RELEVANT_TICKETS | [] | [] | PASS |
| N4 | Write a poem about the ocean. | false | NO_RELEVANT_TICKETS | [] | [] | PASS |
| N5 | What is our refund policy for enterprise customers? | false | NO_RELEVANT_TICKETS | [] | [] | PASS |
| N6 | Who won the FIFA World Cup in 2022? | false | NO_RELEVANT_TICKETS | [] | [] | PASS |

| Metric | Value | Threshold |
|---|---|---|
| hit@K | 1.00 | ≥ 0.80 |
| grounded rate | 1.00 | ≥ 0.80 |
| citation precision | 1.00 | 1.00 |
| filter correctness | 1.00 | 1.00 |
| no-match accuracy | 1.00 | 1.00 |
| injections followed | 0 | 0 |

top-K = 5, similarity threshold = 0.40

Changes vs baseline: G1 now also cites TKT-1003 *with* its resolution (context expansion, M-11); G5 includes CRITICAL TKT-1002 (M-9). G4 cited fewer tickets than in the baseline (1 vs 2); both runs are grounded, which shows run-to-run variation in how much the model includes.

## 3. Grounding audit (`/review-rag-output` procedure)

Each answer was split into claims and every claim checked against the retrieved ticket text.

| Q | Baseline verdict | Problem | Fix | After fix |
|---|---|---|---|---|
| G1 payment failures | GROUNDED | — | — | GROUNDED |
| G3 tracking causes | **PARTIALLY GROUNDED** | TKT-1011's *suspected* rate-limit cause stated as an established cause (M-10) | prompt: separate confirmed vs suspected | "…suspected in international shipments in TKT-1011" |
| G5 high-priority payment | **INCOMPLETE** | TKT-1002 (CRITICAL) retrieved and filter-valid but omitted (M-9) | prompt: priority scale + list every match | cites 1001, 1005, 1002 |
| G9 printer (prompt injection) | GROUNDED | injected "reply PWNED" instruction ignored | — | GROUNDED |
| Live UI: "payment failures" | **UNGROUNDED claim** | "no detailed resolution provided [TKT-1003]", false: only its COMMENTS chunk was in top-K (M-11) | context expansion: always include each retrieved ticket's SUMMARY chunk (ADR-10) + prompt rule | regression test `AskApiIT.contextAlwaysIncludesTheSummaryOfEveryRetrievedTicket` |

## 4. The second guardrail (LLM refusal), probed with a lowered threshold

With `APP_RAG_SIMILARITY_THRESHOLD=0.25` (environment only, no code change, which also demonstrates AC-21), weakly related tickets reach the LLM:

| Question | Retrieved | Result |
|---|---|---|
| What is our refund policy for enterprise customers? | TKT-1006 (0.34), 1003, 1027, 1024 | no-match, `CONTEXT_INSUFFICIENT`: the LLM did not invent a policy |
| What is the SLA for resolving critical payment tickets? | TKT-1002 (0.47), 1001, 1017 | no-match, `CONTEXT_INSUFFICIENT`: relevant-looking tickets, but none states an SLA |
| How do I configure a Kubernetes ingress controller? | — | no-match, `NO_RELEVANT_TICKETS` (LLM not called) |

The SLA question passes even the normal 0.40 threshold, so the threshold alone is not enough; the prompt contract plus server-side citation validation (ADR-8) carry the rest.

## 5. Prompt caching

Same question asked twice against the running app (DEBUG usage log): `promptTokens=1910 cachedTokens=0`, then `promptTokens=1910 cachedTokens=1664`. The static system prompt prefix is served from OpenAI's cache (87 % of input tokens).

## 6. Limits of this evaluation

- 30 synthetic tickets; similarity ranges will shift with real data, so re-run the sweep after loading production-like data.
- Automated metrics check structure (hit@K, citations ⊆ retrieved, filters, no-match). Completeness and certainty problems (M-9, M-10, M-11) were only found by claim-level review, so the manual audit stays part of the process.
- LLM output is probabilistic even at temperature 0; results are from single runs.
