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
}
