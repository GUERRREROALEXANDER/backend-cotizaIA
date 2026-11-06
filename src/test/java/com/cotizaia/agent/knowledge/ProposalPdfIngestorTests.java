package com.cotizaia.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.agent.llm.PromptBuilder;
import com.cotizaia.agent.llm.StubLlmClient;
import com.cotizaia.domain.ChunkKind;
import com.cotizaia.ingest.PdfBriefReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Factory Method: {@link KnowledgeIngestor#ingest} is the fixed algorithm (chunk, persist, embed, index) and each
 * subclass decides which {@link Chunker} it creates. For a past proposal PDF that is a chunker producing both
 * citable passages and grounded line items.
 */
class ProposalPdfIngestorTests {

    private final PastItemExtractor extractor = new PastItemExtractor(new StubLlmClient(), new PromptBuilder(),
            new ObjectMapper());

    @Test
    void createsAProposalPdfChunker() {
        ProposalPdfIngestor ingestor = new ProposalPdfIngestor(null, null, null, null, null, extractor);
        assertThat(ingestor.createChunker()).isInstanceOf(ProposalPdfChunker.class);
    }

    @Test
    void chunkerYieldsBoundedPassagesAndGroundedItems() throws Exception {
        List<String> pages = new PdfBriefReader().readPages(
                Files.readAllBytes(Path.of("seed/pdf/train/P08-propuesta.pdf")));
        IngestionSource source = new IngestionSource(1L, "P08-propuesta.pdf",
                com.cotizaia.domain.KnowledgeDocumentKind.PROPOSAL_PDF, pages, null, PastItemExtractorTests.CATALOG, List.of());

        List<ChunkDraft> drafts = new ProposalPdfChunker(extractor).chunk(source);

        List<ChunkDraft> passages = drafts.stream().filter(d -> d.kind() == ChunkKind.PASSAGE).toList();
        List<ChunkDraft> items = drafts.stream().filter(d -> d.kind() == ChunkKind.ITEM).toList();
        assertThat(passages).isNotEmpty().allSatisfy(passage -> {
            assertThat(passage.text().length()).isBetween(1, ProposalPdfChunker.MAX_PASSAGE_CHARS);
            assertThat(passage.page()).isNotNull().isPositive();
            assertThat(passage.hours()).isNull();
        });
        assertThat(items).isNotEmpty().allSatisfy(item -> {
            assertThat(item.requirementType()).isNotBlank();
            assertThat(item.hours()).isPositive();
            assertThat(item.text()).startsWith(item.requirementType() + ": ");
        });
        assertThat(items).anySatisfy(item -> assertThat(item.text()).contains("300 productos"));
    }
}
