package com.cotizaia.ingest.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GmailRestClientTests {

    private MockRestServiceServer server;

    private GmailRestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GmailRestClient(builder, new GmailProperties.OAuth("cid", "secret", "refresh"), Clock.systemUTC());
    }

    @Test
    void exchangesTheRefreshTokenOnceAndReadsEveryHistoryPage() {
        server.expect(requestTo(GmailRestClient.TOKEN_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
                .andRespond(withSuccess("{\"access_token\":\"at1\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/history?historyTypes=messageAdded")))
                .andExpect(header("Authorization", "Bearer at1"))
                .andRespond(withSuccess("""
                        {"history":[{"messagesAdded":[{"message":{"id":"a"}}]}],"nextPageToken":"p2","historyId":"110"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("pageToken=p2")))
                .andExpect(header("Authorization", "Bearer at1"))
                .andRespond(withSuccess("""
                        {"history":[{"labelsAdded":[{"message":{"id":"b"}}]},{"messagesAdded":[{"message":{"id":"a"}}]}],
                         "historyId":"120"}
                        """, MediaType.APPLICATION_JSON));

        GmailClient.HistoryPage page = client.history(100L, "Label_42");

        assertThat(page.messageIds()).containsExactly("a", "b");
        assertThat(page.historyId()).isEqualTo(120L);
        server.verify();
    }

    @Test
    void aMissingHistoryIdMeansTheCursorExpired() {
        server.expect(requestTo(GmailRestClient.TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"at1\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/history")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.history(1L, "Label_42"))
                .isInstanceOf(GmailClient.HistoryExpiredException.class);
    }

    @Test
    void resolvesALabelNameCaseInsensitively() {
        server.expect(requestTo(GmailRestClient.TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"at1\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(GmailRestClient.API + "/labels")).andRespond(withSuccess(
                "{\"labels\":[{\"id\":\"INBOX\",\"name\":\"INBOX\"},{\"id\":\"Label_42\",\"name\":\"Cotizar\"}]}",
                MediaType.APPLICATION_JSON));

        assertThat(client.labelId("cotizar")).isEqualTo("Label_42");
    }

    @Test
    void refusesToStartWithoutCredentials() {
        assertThatThrownBy(() -> new GmailRestClient(RestClient.builder(), new GmailProperties.OAuth("", null, null),
                Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }
}
