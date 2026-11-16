package com.cotizaia.ingest.gmail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Gmail API resources shaped like users.messages.get?format=full. */
final class GmailFixtures {

    static final String LABEL = "Label_42";

    private GmailFixtures() {
    }

    static String b64(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, Object> plainMessage(String id, String from, String subject, String body, List<String> labels) {
        return Map.of(
                "id", id,
                "labelIds", labels,
                "internalDate", "1762560000000",
                "payload", Map.of(
                        "mimeType", "multipart/alternative",
                        "headers", List.of(
                                Map.of("name", "From", "value", from),
                                Map.of("name", "Subject", "value", subject),
                                Map.of("name", "Message-ID", "value", "<" + id + "@mail.gmail.com>")),
                        "parts", List.of(
                                Map.of("mimeType", "text/plain", "filename", "", "body", Map.of("data", b64(body))),
                                Map.of("mimeType", "text/html", "filename", "",
                                        "body", Map.of("data", b64("<p>" + body + "</p>"))))));
    }
}
