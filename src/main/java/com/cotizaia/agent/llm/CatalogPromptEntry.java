package com.cotizaia.agent.llm;

/** Supplies a catalog name and its keywords to extraction prompts without exposing persistence types. */
public record CatalogPromptEntry(String name, String keywords) {
}
