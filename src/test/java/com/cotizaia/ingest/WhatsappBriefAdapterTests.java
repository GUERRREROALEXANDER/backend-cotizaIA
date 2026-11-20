package com.cotizaia.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.BriefChannel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WhatsappBriefAdapterTests {

    private final WhatsappBriefAdapter adapter = new WhatsappBriefAdapter();

    @Test
    void aChatMessageKeepsItsWordsButNotItsLineBreaks() {
        NormalizedBrief brief = adapter.normalize(Map.of("message",
                "Hola!!\n\nquiero una página\n para mi panadería 🍞"));

        assertThat(brief.rawText()).isEqualTo("Hola!! quiero una página para mi panadería 🍞");
    }

    @Test
    void exactlyFiveThousandCharactersAreAccepted() {
        String atLimit = "a".repeat(PayloadSupport.MAX_TYPED_TEXT_CHARS);

        assertThat(adapter.normalize(Map.of("message", atLimit)).rawText()).hasSize(5_000);
        assertThatThrownBy(() -> adapter.normalize(Map.of("message", atLimit + "a")))
                .hasMessageContaining("message");
    }

    @Test
    void theMessageFieldIsRequired() {
        assertThatThrownBy(() -> adapter.normalize(Map.of("body", "x"))).hasMessageContaining("message");
    }

    @Test
    void declaresTheWhatsappChannel() {
        assertThat(adapter.channel()).isEqualTo(BriefChannel.WHATSAPP);
    }
}
