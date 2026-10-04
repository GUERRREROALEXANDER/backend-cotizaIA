package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
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

class OllamaClientTests {

    private final LlmRequest request = new LlmRequest(LlmTask.CLASSIFY, "system", "user", true);

    @Test
    void sendsChatRequestAndReadsContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"model\":\"llama3.1\",\"stream\":false,"
                        + "\"format\":\"json\",\"options\":{\"temperature\":0},"
                        + "\"messages\":[{\"role\":\"system\",\"content\":\"system\"},"
                        + "{\"role\":\"user\",\"content\":\"user\"}]}"))
                .andRespond(withSuccess("{\"message\":{\"content\":\"answer\"}}", MediaType.APPLICATION_JSON));
        assertThat(new OllamaClient(builder, new LlmProperties()).complete(request).content()).isEqualTo("answer");
        server.verify();
    }

    @Test
    void mapsServerAndUnauthorizedFailures() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withServerError());
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        OllamaClient client = new OllamaClient(builder, new LlmProperties());
        assertThatThrownBy(() -> client.complete(request))
                .matches(error -> error instanceof LlmException llm && llm.isRetryable());
        assertThatThrownBy(() -> client.complete(request))
                .matches(error -> error instanceof LlmException llm && !llm.isRetryable());
        server.verify();
    }
}
