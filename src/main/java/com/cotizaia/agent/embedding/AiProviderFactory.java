package com.cotizaia.agent.embedding;

import com.cotizaia.agent.llm.LlmClient;

/** Abstract Factory: one switch picks a whole provider family, so a chat model is never paired with a foreign embedder. */
public interface AiProviderFactory {

    LlmClient createLlmClient();

    EmbeddingClient createEmbeddingClient();

    String family();
}
