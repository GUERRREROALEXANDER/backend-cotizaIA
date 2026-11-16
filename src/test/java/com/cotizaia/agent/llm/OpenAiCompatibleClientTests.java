package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleClientTests {

    private final LlmRequest request = new LlmRequest(LlmTask.EXTRACT, "system", "user", true);

    private static LlmProperties gemini(String key) {
        LlmProperties properties = new LlmProperties();
        properties.getGemini().setApiKey(key);
        return properties;
    }

    @Test
    void geminiDefaultsTargetGoogleWithGemma4() {
        LlmProperties.Gemini defaults = new LlmProperties().getGemini();

        assertThat(defaults.getBaseUrl()).isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai");
        assertThat(defaults.getModel()).isEqualTo("gemma-4-31b-it");
        assertThat(defaults.getReasoningEffort()).isEmpty();
    }

    @Test
    void sendsTheOpenAiShapedRequestToGoogleAndNamesItselfGemini() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LlmProperties properties = gemini("g-key");
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer g-key"))
                .andExpect(content().json("{\"model\":\"gemma-4-31b-it\",\"temperature\":0,"
                        + "\"response_format\":{\"type\":\"json_object\"}}"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}",
                        MediaType.APPLICATION_JSON));
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(builder, "gemini", "GEMINI_API_KEY",
                properties.getGemini(), properties.getTimeout());

        LlmResponse response = client.complete(request);

        assertThat(response.content()).isEqualTo("answer");
        assertThat(response.provider()).isEqualTo("gemini");
        assertThat(response.model()).isEqualTo("gemma-4-31b-it");
        server.verify();
    }

    @Test
    void namesTheMissingKeyAndTheProviderInTheirErrors() {
        LlmProperties properties = gemini("");
        OpenAiCompatibleClient noKey = new OpenAiCompatibleClient(RestClient.builder(), "gemini", "GEMINI_API_KEY",
                properties.getGemini(), properties.getTimeout());

        assertThatThrownBy(() -> noKey.complete(request))
                .isInstanceOfSatisfying(LlmException.class, error -> assertThat(error.isRetryable()).isFalse())
                .hasMessageContaining("GEMINI_API_KEY");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        OpenAiCompatibleClient limited = new OpenAiCompatibleClient(builder, "gemini", "GEMINI_API_KEY",
                gemini("g-key").getGemini(), properties.getTimeout());

        assertThatThrownBy(() -> limited.complete(request))
                .isInstanceOfSatisfying(LlmException.class, error -> assertThat(error.isRetryable()).isTrue())
                .hasMessage("gemini HTTP 429");
    }
}
