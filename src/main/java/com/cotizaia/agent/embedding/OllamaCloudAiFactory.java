package com.cotizaia.agent.embedding;

import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.LlmProperties;
import com.cotizaia.agent.llm.OllamaClient;
import org.springframework.web.client.RestClient;

/**
 * Ollama Cloud family: Gemma 4 chat over the Ollama contract, Voyage for embeddings because Ollama Cloud
 * hosts no embedding model.
 */
public class OllamaCloudAiFactory implements AiProviderFactory {

    private final RestClient.Builder builder;
    private final LlmProperties llmProperties;
    private final EmbeddingProperties embeddingProperties;

    public OllamaCloudAiFactory(RestClient.Builder builder, LlmProperties llmProperties,
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
        return new VoyageEmbeddingClient(builder, embeddingProperties);
    }

    @Override
    public String family() {
        return "ollama-cloud";
    }
}
