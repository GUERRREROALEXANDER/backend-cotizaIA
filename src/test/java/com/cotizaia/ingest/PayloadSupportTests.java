package com.cotizaia.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayloadSupportTests {

    @Test
    void textIsTrimmedAndInnerWhitespaceCollapsed() {
        Map<String, Object> payload = Map.of("text", "  Hola,\n\n  quiero   una\tpágina  ");

        assertThat(PayloadSupport.requireText(payload, "text")).isEqualTo("Hola, quiero una página");
    }

    @Test
    void theSameMessageWithDifferentSpacingNormalizesTheSame() {
        assertThat(PayloadSupport.normalizeWhitespace("a  b\nc"))
                .isEqualTo(PayloadSupport.normalizeWhitespace(" a b  c "));
    }

    @Test
    void missingOrBlankFieldsAreRejectedWithTheirName() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("blank", "   ");
        payload.put("nothing", null);

        assertThatThrownBy(() -> PayloadSupport.requireText(payload, "blank")).hasMessageContaining("blank");
        assertThatThrownBy(() -> PayloadSupport.requireText(payload, "nothing")).hasMessageContaining("nothing");
        assertThatThrownBy(() -> PayloadSupport.requireText(payload, "absent")).hasMessageContaining("absent");
        assertThatThrownBy(() -> PayloadSupport.requireText(null, "text")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonStringValuesAreReadAsText() {
        assertThat(PayloadSupport.requireText(Map.of("n", 42), "n")).isEqualTo("42");
    }

    @Test
    void exactlyTheLimitIsAcceptedAndOneMoreIsNot() {
        int limit = PayloadSupport.MAX_TYPED_TEXT_CHARS;
        String atLimit = "a".repeat(limit);

        assertThat(PayloadSupport.requireText(Map.of("text", atLimit), "text", limit)).hasSize(limit);
        assertThatThrownBy(() -> PayloadSupport.requireText(Map.of("text", atLimit + "a"), "text", limit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(limit));
    }

    @Test
    void theLimitIsCheckedAfterWhitespaceIsCollapsed() {
        String spaced = "a" + " ".repeat(50) + "b";

        assertThat(PayloadSupport.requireText(Map.of("text", spaced), "text", 3)).isEqualTo("a b");
    }

    @Test
    void typedBriefsAreCappedLikeTheFrontend() {
        assertThat(PayloadSupport.MAX_TYPED_TEXT_CHARS).isEqualTo(5_000);
    }
}
