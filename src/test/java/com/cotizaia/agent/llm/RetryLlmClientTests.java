package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryLlmClientTests {

    private final LlmRequest request = new LlmRequest(LlmTask.SUMMARIZE, "system", "user", false);

    @Test
    void retriesTwiceThenSucceedsWithExponentialDelays() {
        AtomicInteger calls = new AtomicInteger();
        List<Duration> delays = new ArrayList<>();
        LlmClient fake = fake(calls, 2, true);
        RetryLlmClient client = new RetryLlmClient(fake, new LlmProperties.Retry(), delays::add);

        assertThat(client.complete(request).content()).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(delays).containsExactly(Duration.ofMillis(500), Duration.ofMillis(1000));
    }

    @Test
    void doesNotRetryPermanentFailures() {
        AtomicInteger calls = new AtomicInteger();
        RetryLlmClient client = new RetryLlmClient(fake(calls, 3, false),
                new LlmProperties.Retry(), duration -> { });
        assertThatThrownBy(() -> client.complete(request)).isInstanceOf(LlmException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void rethrowsLastFailureWhenAttemptsAreExhausted() {
        AtomicInteger calls = new AtomicInteger();
        RetryLlmClient client = new RetryLlmClient(fake(calls, 3, true),
                new LlmProperties.Retry(), duration -> { });
        assertThatThrownBy(() -> client.complete(request)).isInstanceOf(LlmException.class)
                .matches(exception -> ((LlmException) exception).isRetryable());
        assertThat(calls).hasValue(3);
    }

    private LlmClient fake(AtomicInteger calls, int failures, boolean retryable) {
        return new LlmClient() {
            @Override
            public LlmResponse complete(LlmRequest ignored) {
                if (calls.incrementAndGet() <= failures) {
                    throw new LlmException("failure", retryable);
                }
                return new LlmResponse("ok", "fake", "fake", 0);
            }

            @Override
            public String provider() {
                return "fake";
            }
        };
    }
}
