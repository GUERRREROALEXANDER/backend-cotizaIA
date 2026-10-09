package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GroqClientTests {

    private final LlmRequest request = new LlmRequest(LlmTask.EXTRACT, "system", "user", true);

    @Test
    void sendsAuthorizedJsonRequestAndReadsChoice() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LlmProperties properties = propertiesWithKey();
        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("{\"model\":\"openai/gpt-oss-20b\",\"temperature\":0,"
                        + "\"response_format\":{\"type\":\"json_object\"},"
                        + "\"messages\":[{\"role\":\"system\",\"content\":\"system\"},"
                        + "{\"role\":\"user\",\"content\":\"user\"}]}"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}",
                        MediaType.APPLICATION_JSON));
        assertThat(new GroqClient(builder, properties).complete(request).content()).isEqualTo("answer");
        server.verify();
    }

    @Test
    void mapsServerAndUnauthorizedFailures() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withServerError());
        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        GroqClient client = new GroqClient(builder, propertiesWithKey());
        assertThatThrownBy(() -> client.complete(request))
                .matches(error -> error instanceof LlmException llm && llm.isRetryable());
        assertThatThrownBy(() -> client.complete(request))
                .matches(error -> error instanceof LlmException llm && !llm.isRetryable());
        server.verify();
    }

    @Test
    void missingKeyFailsBeforeHttp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        assertThatThrownBy(() -> new GroqClient(builder, new LlmProperties()).complete(request))
                .isInstanceOf(LlmException.class).hasMessageContaining("GROQ_API_KEY")
                .matches(error -> !((LlmException) error).isRetryable());
        server.verify();
    }

    private LlmProperties propertiesWithKey() {
        LlmProperties properties = new LlmProperties();
        properties.getGroq().setApiKey("test-key");
        return properties;
    }
}
