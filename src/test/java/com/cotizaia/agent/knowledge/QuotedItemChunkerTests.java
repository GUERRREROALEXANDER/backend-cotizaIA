package com.cotizaia.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.domain.ChunkKind;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuotedItemChunkerTests {

    private final QuotedItemChunker chunker = new QuotedItemChunker();

    private static IngestionSource proposalWith(List<ApprovedItem> items) {
        return new IngestionSource(1L, "Propuesta 7", null, List.of(), 7L, List.of(), items);
    }

    @Test
    void everyApprovedLineBecomesOnePricedItemChunk() {
        List<ChunkDraft> drafts = chunker.chunk(proposalWith(List.of(
                new ApprovedItem("WEB_PAGE", "Página de inicio con banner", new BigDecimal("6")),
                new ApprovedItem("SEO", "SEO básico para 4 páginas", new BigDecimal("4.5")))));

        assertThat(drafts).hasSize(2);
        assertThat(drafts).allSatisfy(draft -> {
            assertThat(draft.kind()).isEqualTo(ChunkKind.ITEM);
            assertThat(draft.page()).isNull();
        });
        assertThat(drafts.get(1).requirementType()).isEqualTo("SEO");
        assertThat(drafts.get(1).hours()).isEqualByComparingTo("4.5");
    }

    @Test
    void linesWithoutTextOrHoursAreSkipped() {
        List<ApprovedItem> items = new ArrayList<>();
        items.add(null);
        items.add(new ApprovedItem("WEB_PAGE", "  ", BigDecimal.ONE));
        items.add(new ApprovedItem("WEB_PAGE", null, BigDecimal.ONE));
        items.add(new ApprovedItem("WEB_PAGE", "Contacto", null));
        items.add(new ApprovedItem("WEB_PAGE", "Contacto", BigDecimal.TEN));

        assertThat(chunker.chunk(proposalWith(items))).singleElement()
                .satisfies(draft -> assertThat(draft.text()).isEqualTo("Contacto"));
    }

    @Test
    void aProposalWithoutItemsGivesNoChunks() {
        assertThat(chunker.chunk(proposalWith(null))).isEmpty();
        assertThat(chunker.rejected()).isZero();
    }
}
