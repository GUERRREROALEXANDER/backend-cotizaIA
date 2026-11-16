package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LlmProviderSwitchTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withUserConfiguration(LlmClientConfiguration.class);

    @Test
    void switchesAllAdaptersByProperty() {
        runner.withPropertyValues("llm.provider=ollama").run(context ->
                assertThat(((RetryLlmClient) context.getBean(LlmClient.class)).delegate())
                        .isInstanceOf(OllamaClient.class));
        runner.withPropertyValues("llm.provider=groq").run(context ->
                assertThat(((RetryLlmClient) context.getBean(LlmClient.class)).delegate())
                        .isInstanceOf(GroqClient.class));
        runner.withPropertyValues("llm.provider=stub").run(context ->
                assertThat(context.getBean(LlmClient.class)).isInstanceOf(StubLlmClient.class));
    }

    @Test
    void rejectsUnknownProviderAtStartup() {
        runner.withPropertyValues("llm.provider=other").run(context ->
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("allowed: ollama, groq, stub"));
    }

    @Test
    void buildsAFailoverChainFromTheChainProperty() {
        runner.withPropertyValues("llm.provider=groq", "llm.chain=gemini,groq").run(context -> {
            FallbackLlmClient chain = (FallbackLlmClient) context.getBean(LlmClient.class);
            assertThat(chain.chain()).extracting(LlmClient::provider).containsExactly("gemini", "groq");
            assertThat(chain.chain()).allSatisfy(client -> assertThat(client).isInstanceOf(RetryLlmClient.class));
        });
    }

    @Test
    void anEmptyChainKeepsTheSingleProviderBehavior() {
        runner.withPropertyValues("llm.provider=groq", "llm.chain=").run(context ->
                assertThat(((RetryLlmClient) context.getBean(LlmClient.class)).delegate())
                        .isInstanceOf(GroqClient.class));
    }

    @Test
    void rejectsAnUnknownChainEntryAtStartup() {
        runner.withPropertyValues("llm.provider=groq", "llm.chain=gemini,ollama").run(context ->
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Unknown llm.chain entry: ollama; allowed: gemini, groq"));
    }

    @Test
    void failsOverQuicklyOnEveryClientExceptTheLast() {
        LlmProperties.Retry configured = new LlmProperties.Retry();
        configured.setMaxAttempts(4);
        configured.setMaxBackoff(java.time.Duration.ofSeconds(60));

        LlmProperties.Retry quick = LlmClientConfiguration.failoverRetry(configured);

        assertThat(quick.getMaxAttempts()).isEqualTo(2);
        assertThat(quick.getMaxBackoff()).isEqualTo(java.time.Duration.ofSeconds(3));
        assertThat(quick.getInitialBackoff()).isEqualTo(configured.getInitialBackoff());
        assertThat(configured.getMaxAttempts()).as("the configured retry is not mutated").isEqualTo(4);
    }
}
