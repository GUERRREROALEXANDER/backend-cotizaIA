package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * In JSON mode Groq answers 400 {@code json_validate_failed} when the model happened to emit invalid JSON. It is
 * a sampling accident, not a bad request: the second real eval lost 2 of 6 briefs to it at the Classify step.
 * It must be retryable; any other 400 stays permanent.
 */
class GroqJsonRetryTests {

    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";

    @Test
    void jsonValidationFailureIsRetryable() {
        assertThat(failureFor("{\"error\":{\"message\":\"Failed to generate JSON. Please adjust your prompt.\","
                + "\"type\":\"invalid_request_error\",\"code\":\"json_validate_failed\"}}").isRetryable()).isTrue();
    }

    @Test
    void otherBadRequestsStayPermanent() {
        assertThat(failureFor("{\"error\":{\"message\":\"model not found\",\"type\":\"invalid_request_error\","
                + "\"code\":\"model_not_found\"}}").isRetryable()).isFalse();
    }

    private LlmException failureFor(String body) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body(body));
        LlmProperties properties = new LlmProperties();
        properties.getGroq().setApiKey("k");
        return catchThrowableOfType(() -> new GroqClient(builder, properties)
                .complete(new LlmRequest(LlmTask.CLASSIFY, "s", "u", true)), LlmException.class);
    }
}
