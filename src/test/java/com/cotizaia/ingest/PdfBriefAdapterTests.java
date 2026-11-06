package com.cotizaia.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.BriefChannel;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The PDF channel is one more Adapter behind the {@link BriefSource} port: the
 * upload service hands it the extracted text, and it normalizes like the rest.
 */
class PdfBriefAdapterTests {

    private final PdfBriefAdapter adapter = new PdfBriefAdapter();

    @Test
    void declaresThePdfChannel() {
        assertThat(adapter.channel()).isEqualTo(BriefChannel.PDF);
    }

    @Test
    void normalizesExtractedTextLikeTheOtherChannels() {
        NormalizedBrief brief = adapter.normalize(Map.of("text", "  Necesito   una\n tienda online "));

        assertThat(brief.rawText()).isEqualTo("Necesito una tienda online");
    }

    @Test
    void prependsTheOperatorNoteWhenPresent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", "Tienda online con pagos");
        payload.put("note", "Cliente pide entrega en diciembre");

        String rawText = adapter.normalize(payload).rawText();

        assertThat(rawText).startsWith("Cliente pide entrega en diciembre");
        assertThat(rawText).contains("Tienda online con pagos");
    }

    @Test
    void ignoresABlankNote() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", "Tienda online");
        payload.put("note", "   ");

        assertThat(adapter.normalize(payload).rawText()).isEqualTo("Tienda online");
    }

    @Test
    void requiresTheExtractedText() {
        assertThatThrownBy(() -> adapter.normalize(Map.of("note", "solo nota")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }
}
