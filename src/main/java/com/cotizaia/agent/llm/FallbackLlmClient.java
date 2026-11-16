package com.cotizaia.agent.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chain of Responsibility over LLM providers: tries each client in order and returns the first answer. It moves on
 * only for {@link LlmException} (quota, 5xx, network, bad key, payment); any other exception is a bug and propagates.
 */
public class FallbackLlmClient implements LlmClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackLlmClient.class);

    private final List<LlmClient> chain;

    public FallbackLlmClient(List<LlmClient> chain) {
        Objects.requireNonNull(chain);
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("An LLM failover chain needs at least one client");
        }
        this.chain = List.copyOf(chain);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        List<LlmException> failures = new ArrayList<>();
        for (LlmClient client : chain) {
            try {
                return client.complete(request);
            } catch (LlmException failure) {
                failures.add(failure);
                LOGGER.warn("LLM provider {} failed ({}); trying the next one", client.provider(),
                        failure.getMessage());
            }
        }
        LlmException last = failures.remove(failures.size() - 1);
        failures.forEach(last::addSuppressed);
        throw last;
    }

    @Override
    public String provider() {
        return String.join(">", chain.stream().map(LlmClient::provider).toList());
    }

    public List<LlmClient> chain() {
        return chain;
    }
}
