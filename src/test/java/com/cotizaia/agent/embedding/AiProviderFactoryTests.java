package com.cotizaia.agent.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.agent.llm.LlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Abstract Factory: one switch picks a whole provider family, so a chat model is never paired with a foreign embedder. */
class AiProviderFactoryTests {

    private final RestClient.Builder builder = RestClient.builder();

    @Test
    void eachFactoryBuildsAConsistentFamily() {
        assertFamily(new StubAiFactory(), "stub", "stub", "stub");
        assertFamily(new CloudAiFactory(builder, new LlmProperties(), new EmbeddingProperties()), "cloud", "groq",
                "voyage");
        assertFamily(new OllamaAiFactory(builder, new LlmProperties(), new EmbeddingProperties()), "ollama", "ollama",
                "ollama");
    }

    private void assertFamily(AiProviderFactory factory, String family, String llm, String embedding) {
        assertThat(factory.family()).isEqualTo(family);
        assertThat(factory.createLlmClient().provider()).isEqualTo(llm);
        assertThat(factory.createEmbeddingClient().provider()).isEqualTo(embedding);
    }
}
