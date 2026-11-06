package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * gpt-oss models spend hidden reasoning tokens that count against Groq's free per-minute and per-day budgets.
 * Classification and extraction need little reasoning: "low" cut a call to ~0.7-1.2K tokens with exact results on
 * the seed corpus, after the default effort exhausted the 200K daily budget during the real eval.
 */
class GroqReasoningEffortTests {

    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";

    @Test
    void sendsTheConfiguredReasoningEffort() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andExpect(content().json("{\"reasoning_effort\":\"low\"}"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", MediaType.APPLICATION_JSON));
        LlmProperties properties = new LlmProperties();
        properties.getGroq().setApiKey("k");
        properties.getGroq().setReasoningEffort("low");
        assertThat(new GroqClient(builder, properties).complete(new LlmRequest(LlmTask.CLASSIFY, "s", "u", true))
                .content()).isEqualTo("ok");
        server.verify();
    }

    @Test
    void omitsTheFieldWhenNotConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andExpect(request -> assertThat(
                        ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString())
                        .doesNotContain("reasoning_effort"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", MediaType.APPLICATION_JSON));
        LlmProperties properties = new LlmProperties();
        properties.getGroq().setApiKey("k");
        new GroqClient(builder, properties).complete(new LlmRequest(LlmTask.CLASSIFY, "s", "u", true));
        server.verify();
    }
}
