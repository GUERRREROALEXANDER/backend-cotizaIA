package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The defining property of a Circuit Breaker: after repeated failures it stops calling the provider for a while. */
class CircuitBreakerLlmClientTests {

    private final LlmRequest request = new LlmRequest(LlmTask.EXTRACT, "system", "user", true);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-19T12:00:00Z"));

    @Test
    void staysClosedWhileTheProviderAnswers() {
        Flaky provider = new Flaky("gemini");
        CircuitBreakerLlmClient breaker = new CircuitBreakerLlmClient(provider, 3, Duration.ofSeconds(60), clock);

        breaker.complete(request);
        breaker.complete(request);

        assertThat(breaker.state()).isEqualTo(CircuitBreakerLlmClient.State.CLOSED);
        assertThat(provider.calls).isEqualTo(2);
    }

    @Test
    void opensAfterTheThresholdAndThenSkipsTheProviderWithoutCallingIt() {
        Flaky provider = new Flaky("gemini").failing();
        CircuitBreakerLlmClient breaker = new CircuitBreakerLlmClient(provider, 3, Duration.ofSeconds(60), clock);

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> breaker.complete(request)).isInstanceOf(LlmException.class);
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreakerLlmClient.State.OPEN);

        assertThatThrownBy(() -> breaker.complete(request))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Circuit open")
                .satisfies(error -> assertThat(((LlmException) error).isRetryable()).isFalse());
        assertThat(provider.calls).as("an open circuit never reaches the provider").isEqualTo(3);
    }

    @Test
    void aSuccessResetsTheFailureCount() {
        Flaky provider = new Flaky("gemini").failing();
        CircuitBreakerLlmClient breaker = new CircuitBreakerLlmClient(provider, 3, Duration.ofSeconds(60), clock);

        assertThatThrownBy(() -> breaker.complete(request)).isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> breaker.complete(request)).isInstanceOf(LlmException.class);
        provider.healthy();
        breaker.complete(request);
        provider.failing();
        assertThatThrownBy(() -> breaker.complete(request)).isInstanceOf(LlmException.class);

        assertThat(breaker.state()).isEqualTo(CircuitBreakerLlmClient.State.CLOSED);
    }

    @Test
    void afterTheOpenWindowOneSuccessfulTrialClosesTheCircuit() {
        Flaky provider = new Flaky("gemini").failing();
        CircuitBreakerLlmClient breaker = new CircuitBreakerLlmClient(provider, 1, Duration.ofSeconds(60), clock);
        assertThatThrownBy(() -> breaker.complete(request)).isInstanceOf(LlmException.class);

        clock.advance(Duration.ofSeconds(61));
        provider.healthy();

        assertThat(breaker.complete(request).provider()).isEqualTo("gemini");
        assertThat(breaker.state()).isEqualTo(CircuitBreakerLlmClient.State.CLOSED);
    }

    @Test
    void aFailedTrialOpensTheCircuitForAnotherWindow() {
        Flaky provider = new Flaky("gemini").failing();
        CircuitBreakerLlmClient breaker = new CircuitBreakerLlmClient(provider, 1, Duration.ofSeconds(60), clock);
        assertThatThrownBy(() -> breaker.complete(request)).isInstanceOf(LlmException.class);

        clock.advance(Duration.ofSeconds(61));
        assertThatThrownBy(() -> breaker.complete(request)).hasMessageContaining("down");
        assertThat(breaker.state()).isEqualTo(CircuitBreakerLlmClient.State.OPEN);

        clock.advance(Duration.ofSeconds(30));
        assertThatThrownBy(() -> breaker.complete(request)).hasMessageContaining("Circuit open");
        assertThat(provider.calls).isEqualTo(2);
    }

    @Test
    void insideTheFailoverChainAnOpenCircuitHandsOverToTheNextProviderAtOnce() {
        Flaky gemini = new Flaky("gemini").failing();
        Flaky groq = new Flaky("groq");
        List<Duration> sleeps = new ArrayList<>();
        LlmProperties.Retry retry = new LlmProperties.Retry();
        retry.setMaxAttempts(2);
        LlmClient guardedGemini = new RetryLlmClient(
                new CircuitBreakerLlmClient(gemini, 2, Duration.ofSeconds(60), clock), retry, sleeps::add);
        FallbackLlmClient chain = new FallbackLlmClient(List.of(guardedGemini, groq));

        assertThat(chain.complete(request).provider()).isEqualTo("groq");
        int sleepsWhileClosed = sleeps.size();
        assertThat(chain.complete(request).provider()).isEqualTo("groq");

        assertThat(gemini.calls).as("two failed attempts opened the circuit; the second request skipped gemini")
                .isEqualTo(2);
        assertThat(sleeps).as("no backoff is spent on a provider whose circuit is open").hasSize(sleepsWhileClosed);
    }

    @Test
    void wrapsOnlyProvidersThatHaveASuccessorAndCanBeDisabled() {
        LlmProperties.CircuitBreaker settings = new LlmProperties.CircuitBreaker();
        Flaky gemini = new Flaky("gemini");

        assertThat(LlmClientConfiguration.withCircuitBreaker(gemini, settings))
                .isInstanceOf(CircuitBreakerLlmClient.class);
        settings.setEnabled(false);
        assertThat(LlmClientConfiguration.withCircuitBreaker(gemini, settings)).isSameAs(gemini);
    }

    @Test
    void rejectsAnInvalidConfiguration() {
        Flaky provider = new Flaky("gemini");
        assertThatThrownBy(() -> new CircuitBreakerLlmClient(provider, 0, Duration.ofSeconds(1), clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreakerLlmClient(provider, 1, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class Flaky implements LlmClient {

        private final String name;
        private boolean failing;
        private int calls;

        Flaky(String name) {
            this.name = name;
        }

        Flaky failing() {
            failing = true;
            return this;
        }

        Flaky healthy() {
            failing = false;
            return this;
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            calls++;
            if (failing) {
                throw new LlmException(name + " is down (HTTP 503)", true);
            }
            return new LlmResponse("answer from " + name, name, "model-" + name, 1);
        }

        @Override
        public String provider() {
            return name;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
