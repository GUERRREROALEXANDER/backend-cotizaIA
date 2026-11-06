package com.cotizaia.agent.embedding;

import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.StubLlmClient;

/** Offline family so tests, CI and demos run the pipeline with no network and no API keys. */
public class StubAiFactory implements AiProviderFactory {

    @Override
    public LlmClient createLlmClient() {
        return new StubLlmClient();
    }

    @Override
    public EmbeddingClient createEmbeddingClient() {
        return new StubEmbeddingClient();
    }

    @Override
    public String family() {
        return "stub";
    }
}
