package com.cotizaia.agent.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class VoyageEmbeddingClientTests {

    private static final String URL = "https://api.voyageai.com/v1/embeddings";

    @Test
    void sendsTheVoyageContractAndReordersByIndex() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer voyage-key"))
                .andExpect(content().json("{\"input\":[\"uno\",\"dos\"],\"model\":\"voyage-4-lite\","
                        + "\"input_type\":\"query\",\"output_dimension\":1024}"))
                .andRespond(withSuccess("{\"data\":[{\"embedding\":[0.5,0.5],\"index\":1},"
                        + "{\"embedding\":[1.0,0.0],\"index\":0}],\"usage\":{\"total_tokens\":4}}",
                        MediaType.APPLICATION_JSON));
        List<float[]> vectors = new VoyageEmbeddingClient(builder, properties("voyage-key", 64))
                .embed(List.of("uno", "dos"), EmbeddingPurpose.QUERY);
        assertThat(vectors.get(0)).containsExactly(1.0f, 0.0f);
        assertThat(vectors.get(1)).containsExactly(0.5f, 0.5f);
        server.verify();
    }

    @Test
    void splitsLargeInputsIntoBatchesAndUsesDocumentType() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andExpect(content().json("{\"input\":[\"a\",\"b\"],\"input_type\":\"document\"}"))
                .andRespond(withSuccess("{\"data\":[{\"embedding\":[1],\"index\":0},{\"embedding\":[2],\"index\":1}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andExpect(content().json("{\"input\":[\"c\"]}"))
                .andRespond(withSuccess("{\"data\":[{\"embedding\":[3],\"index\":0}]}", MediaType.APPLICATION_JSON));
        List<float[]> vectors = new VoyageEmbeddingClient(builder, properties("k", 2))
                .embed(List.of("a", "b", "c"), EmbeddingPurpose.DOCUMENT);
        assertThat(vectors).extracting(vector -> vector[0]).containsExactly(1f, 2f, 3f);
        server.verify();
    }

    @Test
    void mapsRateLimitAndServerErrorsAsRetryableAndAuthAsNot() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        VoyageEmbeddingClient client = new VoyageEmbeddingClient(builder, properties("k", 64));
        for (boolean retryable : new boolean[] {true, true, false}) {
            assertThatThrownBy(() -> client.embed(List.of("x"), EmbeddingPurpose.QUERY))
                    .matches(error -> error instanceof EmbeddingException e && e.isRetryable() == retryable);
        }
        server.verify();
    }

    @Test
    void missingKeyFailsBeforeAnyHttpCallAndEmptyInputSkipsHttp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VoyageEmbeddingClient noKey = new VoyageEmbeddingClient(builder, properties(" ", 64));
        assertThatThrownBy(() -> noKey.embed(List.of("x"), EmbeddingPurpose.QUERY))
                .isInstanceOf(EmbeddingException.class);
        assertThat(new VoyageEmbeddingClient(builder, properties("k", 64)).embed(List.of(), EmbeddingPurpose.QUERY))
                .isEmpty();
        server.verify();
        assertThat(noKey.provider()).isEqualTo("voyage");
    }

    private EmbeddingProperties properties(String key, int batchSize) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.getVoyage().setApiKey(key);
        properties.setBatchSize(batchSize);
        return properties;
    }
}
