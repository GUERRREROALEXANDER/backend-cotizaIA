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
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        if (!properties.getChain().isBlank()) {
            return chain(builder, properties);
        }
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

    /**
     * Builds the failover chain from {@code llm.chain}. Every client except the last gets a quick retry so a provider
     * that is out of quota hands over in seconds; the last keeps the configured retry.
     */
    private LlmClient chain(RestClient.Builder builder, LlmProperties properties) {
        applyTimeouts(builder, properties);
        List<String> names = Arrays.stream(properties.getChain().split(",")).map(String::trim).toList();
        List<LlmClient> clients = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            boolean last = index == names.size() - 1;
            LlmProperties.Retry retry = last ? properties.getRetry() : failoverRetry(properties.getRetry());
            LlmClient member = chainMember(builder, properties, names.get(index));
            LlmClient retrying = new RetryLlmClient(member, retry, duration -> Thread.sleep(duration.toMillis()));
            clients.add(last ? retrying : withCircuitBreaker(retrying, properties.getCircuitBreaker()));
        }
        return new FallbackLlmClient(clients);
    }

    /**
     * Wraps a provider (already inside its retry) that has a successor in a Circuit Breaker: while the circuit is open the
     * breaker fails fast with a non-retryable error, so the chain hands over at once instead of retrying a provider
     * that is known to be down. The last provider is never wrapped: with no successor, skipping it gains nothing.
     */
    static LlmClient withCircuitBreaker(LlmClient member, LlmProperties.CircuitBreaker settings) {
        if (!settings.isEnabled()) {
            return member;
        }
        return new CircuitBreakerLlmClient(member, settings.getFailureThreshold(), settings.getOpenDuration(),
                Clock.systemUTC());
    }

    private LlmClient chainMember(RestClient.Builder builder, LlmProperties properties, String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "gemini" -> new OpenAiCompatibleClient(builder, "gemini", "GEMINI_API_KEY", properties.getGemini(),
                    properties.getTimeout());
            case "groq" -> new GroqClient(builder, properties);
            default -> throw new IllegalStateException("Unknown llm.chain entry: " + name + "; allowed: gemini, groq");
        };
    }

    /** Retry for a client that has a successor: at most 2 attempts and a 3 s backoff cap. */
    static LlmProperties.Retry failoverRetry(LlmProperties.Retry configured) {
        LlmProperties.Retry quick = new LlmProperties.Retry();
        quick.setMaxAttempts(Math.min(configured.getMaxAttempts(), 2));
        quick.setInitialBackoff(configured.getInitialBackoff());
        quick.setMultiplier(configured.getMultiplier());
        quick.setMaxBackoff(configured.getMaxBackoff().compareTo(Duration.ofSeconds(3)) < 0
                ? configured.getMaxBackoff() : Duration.ofSeconds(3));
        return quick;
    }

    private static void applyTimeouts(RestClient.Builder builder, LlmProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout().getConnect());
        requestFactory.setReadTimeout(properties.getTimeout().getRead());
        builder.requestFactory(requestFactory);
    }

    /** Maps the configured provider value onto one factory, failing fast on unknown values. */
    private AiProviderFactory factory(RestClient.Builder builder, LlmProperties properties,
            EmbeddingProperties embeddingProperties) {
        applyTimeouts(builder, properties);
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
