package com.cotizaia.ingest.gmail;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec 11 push contract: POST /api/ingest/email answers 401 without a valid
 * Google OIDC token, and 204 (Pub/Sub ack) otherwise, queuing a sync only for
 * our mailbox. No CSRF token and no app session are involved.
 */
@SpringBootTest(properties = {
        "gmail.enabled=true",
        "gmail.scheduler-enabled=false",
        "gmail.mailbox=cotiza@test.co",
        "gmail.agency-id=1",
        "gmail.oauth.client-id=test", "gmail.oauth.client-secret=test", "gmail.oauth.refresh-token=test",
        "gmail.push.enabled=true",
        "gmail.push.audience=https://api.test/api/ingest/email",
        "gmail.push.service-account-email=push@test.iam.gserviceaccount.com"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class GmailPushControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushTokenVerifier verifier;

    @MockitoBean
    private GmailIngestService ingest;

    @BeforeEach
    void setUp() {
        when(ingest.mailbox()).thenReturn("cotiza@test.co");
        when(verifier.verify("good")).thenReturn(true);
        when(verifier.verify("forged")).thenReturn(false);
    }

    @Test
    void rejectsAPushWithoutToken() throws Exception {
        mockMvc.perform(post("/api/ingest/email").contentType(MediaType.APPLICATION_JSON).content(push("cotiza@test.co")))
                .andExpect(status().isUnauthorized());
        verify(ingest, never()).requestSync();
    }

    @Test
    void rejectsAPushWithAnInvalidToken() throws Exception {
        mockMvc.perform(post("/api/ingest/email").header("Authorization", "Bearer forged")
                        .contentType(MediaType.APPLICATION_JSON).content(push("cotiza@test.co")))
                .andExpect(status().isUnauthorized());
        verify(ingest, never()).requestSync();
    }

    @Test
    void acksAndQueuesASyncForOurMailbox() throws Exception {
        mockMvc.perform(post("/api/ingest/email").header("Authorization", "Bearer good")
                        .contentType(MediaType.APPLICATION_JSON).content(push("Cotiza@Test.co")))
                .andExpect(status().isNoContent());
        verify(ingest).requestSync();
    }

    @Test
    void acksWithoutSyncingForAnotherMailboxOrGarbage() throws Exception {
        mockMvc.perform(post("/api/ingest/email").header("Authorization", "Bearer good")
                        .contentType(MediaType.APPLICATION_JSON).content(push("other@test.co")))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/ingest/email").header("Authorization", "Bearer good")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isNoContent());
        verify(ingest, never()).requestSync();
    }

    @Test
    void theRestOfTheApiStillNeedsTheAppToken() throws Exception {
        mockMvc.perform(post("/api/briefs").header("Authorization", "Bearer good")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verify(verifier, never()).verify(anyString());
    }

    private static String push(String mailbox) {
        String data = Base64.getEncoder().encodeToString(
                ("{\"emailAddress\":\"" + mailbox + "\",\"historyId\":\"999\"}").getBytes(StandardCharsets.UTF_8));
        return "{\"message\":{\"data\":\"" + data + "\",\"messageId\":\"1\"},\"subscription\":\"projects/p/subscriptions/s\"}";
    }
}
