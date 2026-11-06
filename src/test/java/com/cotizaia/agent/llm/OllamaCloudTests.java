package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cotizaia.agent.embedding.EmbeddingProperties;
import com.cotizaia.agent.embedding.OllamaCloudAiFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Spec 10: Gemma 4 on Ollama Cloud behind the existing Ollama adapter. */
class OllamaCloudTests {

    private static final String CLOUD = "https://ollama.com";

    @Test
    void sendsTheApiKeyToOllamaCloud() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(CLOUD + "/api/chat")).andExpect(header("Authorization", "Bearer cloud-key"))
                .andRespond(withSuccess("{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}", MediaType.APPLICATION_JSON));
        assertThat(client(builder, "cloud-key").complete(json()).content()).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    void localDaemonGetsNoAuthorizationHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(CLOUD + "/api/chat"))
                .andExpect(request -> assertThat(((MockClientHttpRequest) request).getHeaders().containsKey("Authorization"))
                        .isFalse())
                .andRespond(withSuccess("{\"message\":{\"content\":\"hola\"}}", MediaType.APPLICATION_JSON));
        client(builder, "").complete(new LlmRequest(LlmTask.SUMMARIZE, "s", "u", false));
        server.verify();
    }

    @Test
    void unwrapsAMarkdownFenceOnlyWhenJsonWasRequested() {
        String fenced = "```json\\n{\\\"projectType\\\":\\\"WEB_SITE\\\",\\\"confidence\\\":0.9}\\n```";
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(CLOUD + "/api/chat"))
                .andRespond(withSuccess("{\"message\":{\"content\":\"" + fenced + "\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(CLOUD + "/api/chat"))
                .andRespond(withSuccess("{\"message\":{\"content\":\"  ```\\n{\\\"a\\\":1}\\n```  \"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(CLOUD + "/api/chat"))
                .andRespond(withSuccess("{\"message\":{\"content\":\"" + fenced + "\"}}", MediaType.APPLICATION_JSON));
        OllamaClient client = client(builder, "k");

        assertThat(client.complete(json()).content()).isEqualTo("{\"projectType\":\"WEB_SITE\",\"confidence\":0.9}");
        assertThat(client.complete(json()).content()).isEqualTo("{\"a\":1}");
        assertThat(client.complete(new LlmRequest(LlmTask.SUMMARIZE, "s", "u", false)).content())
                .startsWith("```json");
        server.verify();
    }

    @Test
    void ollamaCloudFamilyPairsGemmaWithVoyageEmbeddings() {
        OllamaCloudAiFactory factory = new OllamaCloudAiFactory(RestClient.builder(), new LlmProperties(),
                new EmbeddingProperties());
        assertThat(factory.family()).isEqualTo("ollama-cloud");
        assertThat(factory.createLlmClient().provider()).isEqualTo("ollama");
        assertThat(factory.createEmbeddingClient().provider()).isEqualTo("voyage");
    }

    private OllamaClient client(RestClient.Builder builder, String key) {
        LlmProperties properties = new LlmProperties();
        properties.getOllama().setBaseUrl(CLOUD);
        properties.getOllama().setModel("gemma4:31b");
        properties.getOllama().setApiKey(key);
        return new OllamaClient(builder, properties);
    }

    private LlmRequest json() {
        return new LlmRequest(LlmTask.CLASSIFY, "s", "u", true);
    }
}
