package com.cotizaia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotizaia.document.DocumentStats;
import com.cotizaia.document.DraftOrigin;
import com.cotizaia.document.DraftParagraph;
import com.cotizaia.document.DraftSection;
import com.cotizaia.document.ProposalDraft;
import com.cotizaia.document.SourceRef;
import com.cotizaia.domain.Proposal;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class ProposalInsightServiceTests {

    private final ApprovalQueueService approval = mock(ApprovalQueueService.class);
    private final ProposalInsightService service = new ProposalInsightService(approval);
    private final Proposal proposal = mock(Proposal.class);

    private void drafted(String json) {
        when(approval.get(1L, 7L)).thenReturn(proposal);
        when(proposal.getDraftedContent()).thenReturn(json);
    }

    private static String draftJson() {
        return new ProposalDraft("Resumen", List.of(new DraftSection("Alcance",
                List.of(new DraftParagraph("Tienda con carrito y pagos.", List.of(1))))),
                List.of(new SourceRef(1, "Propuesta pasada.pdf", 2)), DraftOrigin.AI).toJson();
    }

    @Test
    void exportsTheDraftAsMarkdown() {
        drafted(draftJson());

        assertThat(service.draftMarkdown(1L, 7L)).contains("Alcance").contains("Tienda con carrito y pagos.")
                .contains("Fuentes");
    }

    @Test
    void measuresTheDraft() {
        drafted(draftJson());

        DocumentStats stats = service.draftStats(1L, 7L);

        assertThat(stats.paragraphs()).isEqualTo(1);
        assertThat(stats.citedSources()).isEqualTo(1);
        assertThat(stats.readingMinutes()).isEqualTo(1);
    }

    @Test
    void aProposalWithoutDraftHasNothingToExport() {
        drafted(null);

        assertThatThrownBy(() -> service.draftMarkdown(1L, 7L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("no drafted text");
    }

    @Test
    void anUnreadableDraftIsTreatedAsMissing() {
        drafted("{not json");

        assertThatThrownBy(() -> service.draftStats(1L, 7L)).isInstanceOf(NoSuchElementException.class);
    }
}
