package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.cotizaia.agent.embedding.EmbeddingException;
import com.cotizaia.agent.embedding.EmbeddingProperties;
import com.cotizaia.agent.embedding.EmbeddingPurpose;
import com.cotizaia.agent.embedding.RetryEmbeddingClient;
import com.cotizaia.agent.embedding.VoyageEmbeddingClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Spec 07: free tiers limit per minute (Groq 8K tokens/min, Voyage 3 requests/min) and say how long to wait.
 * Retries must wait that long (capped), not a fixed half second that is guaranteed to hit the limit again.
 */
class RetryAfterTests {

    private final List<Duration> sleeps = new ArrayList<>();

    @Test
    void groqRateLimitCarriesTheProvidersRetryAfter() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "7"));
        LlmProperties properties = new LlmProperties();
        properties.getGroq().setApiKey("k");

        LlmException failure = catchThrowableOfType(() -> new GroqClient(builder, properties)
                .complete(new LlmRequest(LlmTask.CLASSIFY, "s", "u", true)), LlmException.class);

        assertThat(failure.isRetryable()).isTrue();
        assertThat(failure.retryAfter()).contains(Duration.ofSeconds(7));
    }

    @Test
    void voyageRateLimitCarriesTheProvidersRetryAfter() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.voyageai.com/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "20"));
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.getVoyage().setApiKey("k");

        EmbeddingException failure = catchThrowableOfType(() -> new VoyageEmbeddingClient(builder, properties)
                .embed(List.of("x"), EmbeddingPurpose.QUERY), EmbeddingException.class);

        assertThat(failure.retryAfter()).contains(Duration.ofSeconds(20));
    }

    @Test
    void missingOrMalformedRetryAfterIsEmpty() {
        assertThat(new LlmException("x", true).retryAfter()).isEmpty();
        assertThat(new EmbeddingException("x", true).retryAfter()).isEmpty();
        assertThat(RetryAfter.parse("not-a-number")).isEmpty();
        assertThat(RetryAfter.parse("12")).contains(Duration.ofSeconds(12));
        assertThat(RetryAfter.parse("1.5")).contains(Duration.ofMillis(1500));
        assertThat(RetryAfter.parse(null)).isEmpty();
    }

    @Test
    void llmRetryWaitsTheLongerOfBackoffAndRetryAfterCapped() {
        LlmProperties.Retry retry = new LlmProperties.Retry();
        retry.setMaxAttempts(4);
        retry.setInitialBackoff(Duration.ofMillis(500));
        retry.setMultiplier(2.0);
        retry.setMaxBackoff(Duration.ofSeconds(60));
        AtomicInteger calls = new AtomicInteger();
        LlmClient limited = new LlmClient() {
            @Override
            public LlmResponse complete(LlmRequest request) {
                int call = calls.incrementAndGet();
                if (call == 1) {
                    throw new LlmException("429", true, Duration.ofSeconds(18), null);
                }
                if (call == 2) {
                    throw new LlmException("429", true, Duration.ofSeconds(600), null);
                }
                if (call == 3) {
                    throw new LlmException("503", true);
                }
                return new LlmResponse("ok", "limited", "m", 0);
            }

            @Override
            public String provider() {
                return "limited";
            }
        };

        LlmResponse response = new RetryLlmClient(limited, retry, sleeps::add)
                .complete(new LlmRequest(LlmTask.CLASSIFY, "s", "u", true));

        assertThat(response.content()).isEqualTo("ok");
        assertThat(sleeps).containsExactly(Duration.ofSeconds(18), Duration.ofSeconds(60), Duration.ofSeconds(2));
    }

    @Test
    void embeddingRetryHonorsRetryAfterToo() {
        EmbeddingProperties.Retry retry = new EmbeddingProperties.Retry();
        retry.setMaxAttempts(3);
        retry.setInitialBackoff(Duration.ofSeconds(5));
        retry.setMultiplier(2.0);
        retry.setMaxBackoff(Duration.ofSeconds(60));
        AtomicInteger calls = new AtomicInteger();
        com.cotizaia.agent.embedding.EmbeddingClient limited = new com.cotizaia.agent.embedding.EmbeddingClient() {
            @Override
            public List<float[]> embed(List<String> texts, EmbeddingPurpose purpose) {
                if (calls.incrementAndGet() == 1) {
                    throw new EmbeddingException("429", true, Duration.ofSeconds(21), null);
                }
                return List.of(new float[] {1f});
            }

            @Override
            public String provider() {
                return "limited";
            }
        };
        new RetryEmbeddingClient(limited, retry, sleeps::add).embed(List.of("x"), EmbeddingPurpose.QUERY);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(21));
    }
}
