package com.cotizaia.agent.llm;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Selects one adapter by configuration and applies retries to network providers at the boundary. */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmClientConfiguration {

    @Bean
    public LlmClient llmClient(RestClient.Builder builder, LlmProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout().getConnect());
        factory.setReadTimeout(properties.getTimeout().getRead());
        builder.requestFactory(factory);
        Map<String, Supplier<LlmClient>> providers = Map.of(
                "ollama", () -> new OllamaClient(builder, properties),
                "groq", () -> new GroqClient(builder, properties),
                "stub", StubLlmClient::new);
        String selected = properties.getProvider() == null ? "" : properties.getProvider().toLowerCase(Locale.ROOT);
        Supplier<LlmClient> supplier = providers.get(selected);
        if (supplier == null) {
            throw new IllegalStateException("Unknown llm.provider: " + selected + "; allowed: ollama, groq, stub");
        }
        LlmClient raw = supplier.get();
        if (raw instanceof StubLlmClient) {
            return raw;
        }
        return new RetryLlmClient(raw, properties.getRetry(), duration -> Thread.sleep(duration.toMillis()));
    }
}
