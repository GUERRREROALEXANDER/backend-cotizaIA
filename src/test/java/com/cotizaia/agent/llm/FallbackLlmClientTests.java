package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FallbackLlmClientTests {

    private final LlmRequest request = new LlmRequest(LlmTask.EXTRACT, "system", "user", true);

    /** Records every call and either answers or throws, so the tests watch real call order. */
    private static final class Scripted implements LlmClient {

        private final String name;
        private final RuntimeException failure;
        private final List<String> calls;

        Scripted(String name, RuntimeException failure, List<String> calls) {
            this.name = name;
            this.failure = failure;
            this.calls = calls;
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            calls.add(name);
            if (failure != null) {
                throw failure;
            }
            return new LlmResponse("answer from " + name, name, "model-" + name, 1);
        }

        @Override
        public String provider() {
            return name;
        }
    }

    @Test
    void answersFromTheFirstClientAndNeverCallsTheRest() {
        List<String> calls = new ArrayList<>();
        FallbackLlmClient chain = new FallbackLlmClient(List.of(
                new Scripted("gemini", null, calls), new Scripted("groq", null, calls)));

        LlmResponse response = chain.complete(request);

        assertThat(response.provider()).isEqualTo("gemini");
        assertThat(calls).containsExactly("gemini");
    }

    @Test
    void movesOnToTheNextClientWhenOneFailsWithAnLlmException() {
        List<String> calls = new ArrayList<>();
        FallbackLlmClient chain = new FallbackLlmClient(List.of(
                new Scripted("gemini", new LlmException("gemini HTTP 429", true), calls),
                new Scripted("groq", null, calls)));

        LlmResponse response = chain.complete(request);

        assertThat(response.provider()).isEqualTo("groq");
        assertThat(response.content()).isEqualTo("answer from groq");
        assertThat(calls).containsExactly("gemini", "groq");
    }

    @Test
    void movesOnEvenWhenTheFailureIsNotRetryable() {
        // 402 (payment) and 401 (bad key) are not retryable on the same provider, but another provider can answer.
        List<String> calls = new ArrayList<>();
        FallbackLlmClient chain = new FallbackLlmClient(List.of(
                new Scripted("gemini", new LlmException("gemini HTTP 402", false), calls),
                new Scripted("groq", null, calls)));

        assertThat(chain.complete(request).provider()).isEqualTo("groq");
    }

    @Test
    void throwsTheLastFailureWithTheEarlierOnesAttachedWhenEveryClientFails() {
        List<String> calls = new ArrayList<>();
        LlmException first = new LlmException("gemini HTTP 429", true);
        LlmException second = new LlmException("groq HTTP 503", true);
        FallbackLlmClient chain = new FallbackLlmClient(List.of(
                new Scripted("gemini", first, calls), new Scripted("groq", second, calls)));

        assertThatThrownBy(() -> chain.complete(request))
                .isSameAs(second)
                .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(first));
        assertThat(calls).containsExactly("gemini", "groq");
    }

    @Test
    void doesNotSwallowAProgrammingError() {
        List<String> calls = new ArrayList<>();
        FallbackLlmClient chain = new FallbackLlmClient(List.of(
                new Scripted("gemini", new IllegalStateException("bug"), calls),
                new Scripted("groq", null, calls)));

        assertThatThrownBy(() -> chain.complete(request)).isInstanceOf(IllegalStateException.class);
        assertThat(calls).containsExactly("gemini");
    }

    @Test
    void rejectsAnEmptyChain() {
        assertThatThrownBy(() -> new FallbackLlmClient(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsTheChainOrderAsItsProvider() {
        FallbackLlmClient chain = new FallbackLlmClient(List.of(
                new Scripted("gemini", null, new ArrayList<>()), new Scripted("groq", null, new ArrayList<>())));

        assertThat(chain.provider()).isEqualTo("gemini>groq");
        assertThat(chain.chain()).extracting(LlmClient::provider).containsExactly("gemini", "groq");
    }
}
