package com.cotizaia.agent.embedding;

import com.cotizaia.agent.llm.GroqClient;
import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.LlmProperties;
import org.springframework.web.client.RestClient;

/** Cloud family: Groq for chat, Voyage for embeddings. Both share the same HTTP builder. */
public class CloudAiFactory implements AiProviderFactory {

    private final RestClient.Builder builder;
    private final LlmProperties llmProperties;
    private final EmbeddingProperties embeddingProperties;

    public CloudAiFactory(RestClient.Builder builder, LlmProperties llmProperties,
            EmbeddingProperties embeddingProperties) {
        this.builder = builder;
        this.llmProperties = llmProperties;
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    public LlmClient createLlmClient() {
        return new GroqClient(builder, llmProperties);
    }

    @Override
    public EmbeddingClient createEmbeddingClient() {
        return new VoyageEmbeddingClient(builder, embeddingProperties);
    }

    @Override
    public String family() {
        return "cloud";
    }
}
