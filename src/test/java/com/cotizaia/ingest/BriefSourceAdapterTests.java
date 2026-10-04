package com.cotizaia.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the channel adapters: different payload shapes must reduce to
 * the same normalized brief, and audit-only fields must not leak into it.
 */
class BriefSourceAdapterTests {

    private final EmailBriefAdapter email = new EmailBriefAdapter();
    private final WhatsappBriefAdapter whatsapp = new WhatsappBriefAdapter();
    private final WebFormBriefAdapter webForm = new WebFormBriefAdapter();

    private static final String CANONICAL = "Necesito una pagina web para mi restaurante";

    @Test
    void differentPayloadShapesNormalizeToTheSameBrief() {
        String fromEmail = email.normalize(Map.of(
                "from", "cliente@elsabor.co",
                "subject", "Cotizacion web",
                "body", "  Necesito   una pagina web\npara mi restaurante  ")).rawText();
        String fromWhatsapp = whatsapp.normalize(Map.of(
                "from", "+573001112233",
                "message", "Necesito una pagina web para mi restaurante")).rawText();
        String fromForm = webForm.normalize(Map.of(
                "projectType", "Web",
                "budget", "3000000",
                "description", CANONICAL)).rawText();

        // Email and WhatsApp collapse to the canonical text exactly.
        assertThat(fromEmail).isEqualTo(CANONICAL);
        assertThat(fromWhatsapp).isEqualTo(CANONICAL);
        // The form prepends its structured project type, so its text starts with it.
        assertThat(fromForm).isEqualTo("Web: " + CANONICAL);
    }

    @Test
    void auditOnlyFieldsDoNotChangeTheNormalizedText() {
        String withoutSubject = email.normalize(Map.of("body", CANONICAL)).rawText();
        String withSubject = email.normalize(Map.of(
                "subject", "Asunto irrelevante",
                "from", "otro@cliente.co",
                "body", CANONICAL)).rawText();

        assertThat(withSubject).isEqualTo(withoutSubject);
    }

    @Test
    void whitespaceIsNormalizedIdenticallyAcrossAdapters() {
        String messy = "  Hola,\n\n\tnecesito   un   logo  ";
        assertThat(email.normalize(Map.of("body", messy)).rawText())
                .isEqualTo("Hola, necesito un logo");
        assertThat(whatsapp.normalize(Map.of("message", messy)).rawText())
                .isEqualTo("Hola, necesito un logo");
    }

    @Test
    void missingRequiredFieldFailsFast() {
        assertThatThrownBy(() -> email.normalize(Map.of("subject", "no body")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body");
        assertThatThrownBy(() -> whatsapp.normalize(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
        assertThatThrownBy(() -> webForm.normalize(Map.of("projectType", "Web")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void eachAdapterDeclaresItsChannel() {
        assertThat(email.channel()).isEqualTo(com.cotizaia.domain.BriefChannel.EMAIL);
        assertThat(whatsapp.channel()).isEqualTo(com.cotizaia.domain.BriefChannel.WHATSAPP);
        assertThat(webForm.channel()).isEqualTo(com.cotizaia.domain.BriefChannel.WEB_FORM);
    }
}
