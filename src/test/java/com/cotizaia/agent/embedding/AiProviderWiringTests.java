package com.cotizaia.agent.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.agent.llm.LlmClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The application context exposes one provider family, with the embedding cache (Proxy) in front. */
@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
class AiProviderWiringTests {

    @Autowired
    private LlmClient llm;

    @Autowired
    private EmbeddingClient embeddings;

    @Test
    void contextExposesTheStubFamilyWithTheEmbeddingCacheInFront() {
        assertThat(llm.provider()).isEqualTo("stub");
        assertThat(embeddings).isInstanceOf(CachingEmbeddingClient.class);
        assertThat(embeddings.provider()).isEqualTo("stub");
        assertThat(embeddings.embed(List.of("hola"), EmbeddingPurpose.QUERY).get(0)).hasSize(256);
    }
}
