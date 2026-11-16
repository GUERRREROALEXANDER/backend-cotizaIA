package com.cotizaia.ingest.gmail;

import static com.cotizaia.ingest.gmail.GmailFixtures.b64;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GmailMessageParserTests {

    @Test
    void readsSenderSubjectPlainBodyLabelsAndDate() {
        GmailMessage message = GmailMessageParser.parse(GmailFixtures.plainMessage("m1",
                "\"Laura Gómez\" <Laura@Sabor.co>", "Cotización app", "Necesitamos una app de pedidos.",
                List.of("INBOX", GmailFixtures.LABEL)));

        assertThat(message.id()).isEqualTo("m1");
        assertThat(message.fromAddress()).isEqualTo("laura@sabor.co");
        assertThat(message.fromName()).isEqualTo("Laura Gómez");
        assertThat(message.subject()).isEqualTo("Cotización app");
        assertThat(message.body()).isEqualTo("Necesitamos una app de pedidos.");
        assertThat(message.messageIdHeader()).isEqualTo("<m1@mail.gmail.com>");
        assertThat(message.labelIds()).containsExactly("INBOX", GmailFixtures.LABEL);
        assertThat(message.receivedAt()).isEqualTo(Instant.ofEpochMilli(1762560000000L));
        assertThat(message.truncated()).isFalse();
    }

    @Test
    void bareAddressIsAlsoTheName() {
        GmailMessage message = GmailMessageParser.parse(
                GmailFixtures.plainMessage("m2", "ops@acme.co", "", "Hola", List.of()));

        assertThat(message.fromAddress()).isEqualTo("ops@acme.co");
        assertThat(message.fromName()).isEqualTo("ops@acme.co");
    }

    @Test
    void fallsBackToHtmlWithTagsStrippedWhenThereIsNoPlainPart() {
        Map<String, Object> resource = Map.of("id", "m3", "payload", Map.of(
                "mimeType", "text/html", "filename", "",
                "headers", List.of(Map.of("name", "From", "value", "a@b.co")),
                "body", Map.of("data", b64("<div>Landing &amp; tienda<br>online</div><style>p{}</style>"))));

        GmailMessage message = GmailMessageParser.parse(resource);

        assertThat(message.body()).contains("Landing & tienda").contains("online").doesNotContain("<").doesNotContain("p{}");
    }

    @Test
    void skipsAttachmentsWhenLookingForTheBody() {
        Map<String, Object> resource = Map.of("id", "m4", "payload", Map.of(
                "mimeType", "multipart/mixed",
                "headers", List.of(Map.of("name", "From", "value", "a@b.co")),
                "parts", List.of(
                        Map.of("mimeType", "text/plain", "filename", "notes.txt", "body", Map.of("data", b64("attached"))),
                        Map.of("mimeType", "text/plain", "filename", "", "body", Map.of("data", b64("the brief"))))));

        assertThat(GmailMessageParser.parse(resource).body()).isEqualTo("the brief");
    }

    @Test
    void dropsTheQuotedPartOfAReply() {
        String body = "Sí, también queremos pagos.\n\nEl lun, 3 nov 2026 a las 10:00, Ana <ana@x.co> escribió:\n> texto viejo";

        GmailMessage message = GmailMessageParser.parse(
                GmailFixtures.plainMessage("m5", "a@b.co", "Re: app", body, List.of()));

        assertThat(message.body()).isEqualTo("Sí, también queremos pagos.");
    }

    @Test
    void capsTheBodyAtTheAdapterLimit() {
        GmailMessage message = GmailMessageParser.parse(GmailFixtures.plainMessage("m6", "a@b.co", "",
                "x".repeat(GmailMessageParser.MAX_BODY_CHARS + 10), List.of()));

        assertThat(message.body()).hasSize(GmailMessageParser.MAX_BODY_CHARS);
        assertThat(message.truncated()).isTrue();
    }
}
