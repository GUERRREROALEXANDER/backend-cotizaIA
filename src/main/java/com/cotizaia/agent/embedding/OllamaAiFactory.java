package com.cotizaia.agent.embedding;

import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.LlmProperties;
import com.cotizaia.agent.llm.OllamaClient;
import org.springframework.web.client.RestClient;

/** Self-hosted family: everything talks to the same local Ollama deployment. */
public class OllamaAiFactory implements AiProviderFactory {

    private final RestClient.Builder builder;
    private final LlmProperties llmProperties;
    private final EmbeddingProperties embeddingProperties;

    public OllamaAiFactory(RestClient.Builder builder, LlmProperties llmProperties,
            EmbeddingProperties embeddingProperties) {
        this.builder = builder;
        this.llmProperties = llmProperties;
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    public LlmClient createLlmClient() {
        return new OllamaClient(builder, llmProperties);
    }

    @Override
    public EmbeddingClient createEmbeddingClient() {
        return new OllamaEmbeddingClient(builder, embeddingProperties);
    }

    @Override
    public String family() {
        return "ollama";
    }
}
