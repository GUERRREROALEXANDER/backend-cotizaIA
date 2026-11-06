package com.cotizaia.agent.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.agent.llm.LlmClientConfiguration;
import com.cotizaia.agent.llm.LlmProperties;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Decorator: transient embedding failures (429 rate limit, 5xx) are retried with capped exponential backoff, so a
 * free-tier provider limit (Voyage without billing: 3 requests per minute) slows ingestion down instead of failing it.
 */
class RetryEmbeddingClientTests {

    private final List<Duration> sleeps = new ArrayList<>();

    @Test
    void retriesTransientFailuresWithExponentialBackoffThenSucceeds() {
        FlakyClient flaky = new FlakyClient(retryable(), retryable(), null);
        List<float[]> vectors = new RetryEmbeddingClient(flaky, retry(5, 5, 60), sleeps::add)
                .embed(List.of("x"), EmbeddingPurpose.DOCUMENT);
        assertThat(vectors).hasSize(1);
        assertThat(flaky.calls).isEqualTo(3);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    @Test
    void neverRetriesPermanentFailures() {
        FlakyClient flaky = new FlakyClient(new EmbeddingException("401", false), null);
        assertThatThrownBy(() -> new RetryEmbeddingClient(flaky, retry(5, 5, 60), sleeps::add)
                .embed(List.of("x"), EmbeddingPurpose.QUERY)).isInstanceOf(EmbeddingException.class);
        assertThat(flaky.calls).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void givesUpAfterMaxAttemptsAndCapsTheBackoff() {
        FlakyClient flaky = new FlakyClient(retryable(), retryable(), retryable(), retryable(), null);
        assertThatThrownBy(() -> new RetryEmbeddingClient(flaky, retry(4, 20, 30), sleeps::add)
                .embed(List.of("x"), EmbeddingPurpose.QUERY))
                .isInstanceOf(EmbeddingException.class)
                .matches(error -> ((EmbeddingException) error).isRetryable());
        assertThat(flaky.calls).isEqualTo(4);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(20), Duration.ofSeconds(30), Duration.ofSeconds(30));
    }

    @Test
    void networkFamiliesGetRetryInsideTheCacheAndTheStubDoesNot() {
        LlmClientConfiguration configuration = new LlmClientConfiguration();
        EmbeddingProperties embedding = new EmbeddingProperties();

        LlmProperties groq = new LlmProperties();
        groq.setProvider("groq");
        EmbeddingClient cloud = configuration.embeddingClient(RestClient.builder(), groq, embedding);
        assertThat(cloud).isInstanceOf(CachingEmbeddingClient.class);
        assertThat(((CachingEmbeddingClient) cloud).delegate()).isInstanceOf(RetryEmbeddingClient.class);
        assertThat(cloud.provider()).isEqualTo("voyage");

        LlmProperties stub = new LlmProperties();
        stub.setProvider("stub");
        EmbeddingClient offline = configuration.embeddingClient(RestClient.builder(), stub, embedding);
        assertThat(((CachingEmbeddingClient) offline).delegate()).isInstanceOf(StubEmbeddingClient.class);
    }

    private static EmbeddingException retryable() {
        return new EmbeddingException("429 Too Many Requests", true);
    }

    private static EmbeddingProperties.Retry retry(int attempts, int initialSeconds, int maxSeconds) {
        EmbeddingProperties.Retry retry = new EmbeddingProperties.Retry();
        retry.setMaxAttempts(attempts);
        retry.setInitialBackoff(Duration.ofSeconds(initialSeconds));
        retry.setMultiplier(2.0);
        retry.setMaxBackoff(Duration.ofSeconds(maxSeconds));
        return retry;
    }

    /** Throws the scripted exceptions in order; a null entry means "succeed". */
    static class FlakyClient implements EmbeddingClient {

        private final Deque<EmbeddingException> script = new ArrayDeque<>();

        int calls;

        FlakyClient(EmbeddingException... outcomes) {
            for (EmbeddingException outcome : outcomes) {
                script.add(outcome == null ? new EmbeddingException("ok", false) : outcome);
            }
        }

        @Override
        public List<float[]> embed(List<String> texts, EmbeddingPurpose purpose) {
            calls++;
            EmbeddingException next = script.poll();
            if (next != null && !"ok".equals(next.getMessage())) {
                throw next;
            }
            return texts.stream().map(text -> new float[] {1f}).toList();
        }

        @Override
        public String provider() {
            return "flaky";
        }
    }
}
