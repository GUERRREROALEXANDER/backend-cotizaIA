package com.cotizaia.agent.llm;

import com.cotizaia.agent.embedding.AiProviderFactory;
import com.cotizaia.agent.embedding.CachingEmbeddingClient;
import com.cotizaia.agent.embedding.CloudAiFactory;
import com.cotizaia.agent.embedding.EmbeddingClient;
import com.cotizaia.agent.embedding.EmbeddingProperties;
import com.cotizaia.agent.embedding.OllamaAiFactory;
import com.cotizaia.agent.embedding.OllamaCloudAiFactory;
import com.cotizaia.agent.embedding.RetryEmbeddingClient;
import com.cotizaia.agent.embedding.StubAiFactory;
import com.cotizaia.agent.embedding.StubEmbeddingClient;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Selects one provider family (Abstract Factory) by configuration, applies retries to network chat
 * providers at the boundary, and always fronts embeddings with the caching Proxy.
 */
@Configuration
@EnableConfigurationProperties({LlmProperties.class, EmbeddingProperties.class})
public class LlmClientConfiguration {

    @Bean
    public LlmClient llmClient(RestClient.Builder builder, LlmProperties properties,
            EmbeddingProperties embeddingProperties) {
        LlmClient raw = factory(builder, properties, embeddingProperties).createLlmClient();
        if (raw instanceof StubLlmClient) {
            return raw;
        }
        return new RetryLlmClient(raw, properties.getRetry(), duration -> Thread.sleep(duration.toMillis()));
    }

    @Bean
    public EmbeddingClient embeddingClient(RestClient.Builder builder, LlmProperties properties,
            EmbeddingProperties embeddingProperties) {
        EmbeddingClient raw = factory(builder, properties, embeddingProperties).createEmbeddingClient();
        if (raw instanceof StubEmbeddingClient) {
            return new CachingEmbeddingClient(raw, embeddingProperties.getCacheSize());
        }
        EmbeddingClient retrying =
                new RetryEmbeddingClient(raw, embeddingProperties.getRetry(), duration -> Thread.sleep(duration.toMillis()));
        return new CachingEmbeddingClient(retrying, embeddingProperties.getCacheSize());
    }

    /** Maps the configured provider value onto one factory, failing fast on unknown values. */
    private AiProviderFactory factory(RestClient.Builder builder, LlmProperties properties,
            EmbeddingProperties embeddingProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout().getConnect());
        requestFactory.setReadTimeout(properties.getTimeout().getRead());
        builder.requestFactory(requestFactory);
        String selected = properties.getProvider() == null ? "" : properties.getProvider().toLowerCase(Locale.ROOT);
        Map<String, Supplier<AiProviderFactory>> families = Map.of(
                "ollama", () -> new OllamaAiFactory(builder, properties, embeddingProperties),
                "groq", () -> new CloudAiFactory(builder, properties, embeddingProperties),
                "stub", StubAiFactory::new,
                "ollama-cloud", () -> new OllamaCloudAiFactory(builder, properties, embeddingProperties));
        Supplier<AiProviderFactory> supplier = families.get(selected);
        if (supplier == null) {
            throw new IllegalStateException(
                    "Unknown llm.provider: " + selected + "; allowed: ollama, groq, stub, ollama-cloud");
        }
        return supplier.get();
    }
}
