package com.example.tickets.ai.ask;

/** Why an /ask response is the no-match response (spec/rag-api-contract.md §1). */
public enum NoMatchReason {
    /** Nothing passed the similarity threshold / filter; the LLM was not called. */
    NO_RELEVANT_TICKETS,
    /** The LLM said the retrieved tickets do not answer the question. */
    CONTEXT_INSUFFICIENT,
    /** The answer cited or mentioned tickets that were not retrieved, or cited none. */
    UNVERIFIED_CITATIONS,
    /** The LLM output did not match the JSON contract. */
    INVALID_MODEL_OUTPUT
}
