package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CitationJsonTests {

    private static final Citation CAFE = new Citation(12L, "Propuesta Café Galeras.pdf", 3, 0.87,
            new BigDecimal("6.5"));

    @Test
    void citationsSurviveTheRoundTrip() {
        List<Citation> parsed = Citation.fromJson(Citation.toJson(List.of(CAFE)));

        assertThat(parsed).singleElement().satisfies(citation -> {
            assertThat(citation.chunkId()).isEqualTo(12L);
            assertThat(citation.documentName()).isEqualTo("Propuesta Café Galeras.pdf");
            assertThat(citation.page()).isEqualTo(3);
            assertThat(citation.similarity()).isEqualTo(0.87);
            assertThat(citation.hours()).isEqualByComparingTo("6.5");
        });
    }

    @Test
    void noCitationsAreStoredAsNull() {
        assertThat(Citation.toJson(null)).isNull();
        assertThat(Citation.toJson(List.of())).isNull();
    }

    @Test
    void emptyOrBrokenStoredValuesReadAsNoCitations() {
        assertThat(Citation.fromJson(null)).isEmpty();
        assertThat(Citation.fromJson(" ")).isEmpty();
        assertThat(Citation.fromJson("{broken")).isEmpty();
        assertThat(Citation.fromJson("null")).isEmpty();
    }

    @Test
    void parsedCitationsCannotBeModified() {
        List<Citation> parsed = Citation.fromJson(Citation.toJson(List.of(CAFE)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parsed.add(CAFE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
