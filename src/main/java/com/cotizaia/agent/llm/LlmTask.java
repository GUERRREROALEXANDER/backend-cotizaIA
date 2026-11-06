package com.cotizaia.agent.llm;

/** Identifies the prompt contract used by an LLM request. */
public enum LlmTask {
    CLASSIFY,
    EXTRACT,
    EXTRACT_ITEMS,
    SUMMARIZE,
    DRAFT_PROPOSAL
}
