package com.cotizaia.ingest.gmail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pub/Sub push endpoint for Gmail notifications (spec 11). It verifies the
 * OIDC token, checks the notification names our mailbox, queues a sync and
 * answers 204 at once: Pub/Sub acks any 2xx, and the quotation pipeline takes
 * minutes, far longer than the push deadline.
 *
 * <p>Answers 204 (ack) also for a well-signed push that names another mailbox
 * or carries an unreadable body: a non-2xx would only make Pub/Sub redeliver
 * the same useless message until it expires.
 */
@RestController
@ConditionalOnProperty(name = {"gmail.enabled", "gmail.push.enabled"}, havingValue = "true")
public class GmailPushController {

    private final GmailIngestService ingest;

    private final PushTokenVerifier verifier;

    private final ObjectMapper objectMapper;

    public GmailPushController(GmailIngestService ingest, PushTokenVerifier verifier, ObjectMapper objectMapper) {
        this.ingest = ingest;
        this.verifier = verifier;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/ingest/email")
    public ResponseEntity<Void> push(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestBody(required = false) String body) {
        if (auth == null || !auth.startsWith("Bearer ") || !verifier.verify(auth.substring(7).trim())) {
            return ResponseEntity.status(401).build();
        }
        if (mailboxMatches(body)) {
            ingest.requestSync();
        }
        return ResponseEntity.noContent().build();
    }

    private boolean mailboxMatches(String body) {
        try {
            JsonNode data = objectMapper.readTree(body).path("message").path("data");
            JsonNode notification = objectMapper.readTree(
                    new String(Base64.getDecoder().decode(data.asText()), StandardCharsets.UTF_8));
            return ingest.mailbox().equalsIgnoreCase(notification.path("emailAddress").asText());
        } catch (Exception e) {
            return false;
        }
    }
}
