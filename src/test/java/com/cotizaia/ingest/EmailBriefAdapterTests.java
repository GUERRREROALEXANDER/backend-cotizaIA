package com.cotizaia.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailBriefAdapterTests {

    private final EmailBriefAdapter adapter = new EmailBriefAdapter();

    @Test
    void anIsoReceivedAtIsKept() {
        NormalizedBrief brief = adapter.normalize(Map.of("body", "Necesito una web", "receivedAt",
                "2026-11-20T22:00:00Z"));

        assertThat(brief.receivedAt()).isEqualTo(Instant.parse("2026-11-20T22:00:00Z"));
    }

    @Test
    void anUnreadableReceivedAtIsDroppedInsteadOfFailing() {
        NormalizedBrief brief = adapter.normalize(Map.of("body", "Necesito una web", "receivedAt", "ayer"));

        assertThat(brief.rawText()).isEqualTo("Necesito una web");
        assertThat(brief.receivedAt()).isNull();
    }

    @Test
    void withoutReceivedAtTheTimeIsUnknown() {
        assertThat(adapter.normalize(Map.of("body", "x")).receivedAt()).isNull();
    }

    @Test
    void theSubjectIsNotPartOfTheBriefText() {
        assertThat(adapter.normalize(Map.of("subject", "Cotización", "body", "Tienda online")).rawText())
                .isEqualTo("Tienda online");
    }

    @Test
    void bodiesOverTheTypedLimitAreRejected() {
        String tooLong = "a".repeat(PayloadSupport.MAX_TYPED_TEXT_CHARS + 1);

        assertThatThrownBy(() -> adapter.normalize(Map.of("body", tooLong))).hasMessageContaining("body");
    }
}
