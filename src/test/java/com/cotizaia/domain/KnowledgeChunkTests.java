package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class KnowledgeChunkTests {

    private final KnowledgeDocument document = new KnowledgeDocument(new Agency("Estudio Nariño Digital"),
            "Propuesta.pdf", KnowledgeDocumentKind.PROPOSAL_PDF);

    @Test
    void aPassageKeepsItsTextAndPage() {
        KnowledgeChunk chunk = new KnowledgeChunk(document, ChunkKind.PASSAGE, "Tienda con carrito.", 3, null, null, 0);

        assertThat(chunk.getKind()).isEqualTo(ChunkKind.PASSAGE);
        assertThat(chunk.getPage()).isEqualTo(3);
        assertThat(chunk.getHours()).isNull();
        assertThat(chunk.getDocument()).isSameAs(document);
    }

    @Test
    void aQuotedItemKeepsItsTypeAndHours() {
        KnowledgeChunk chunk = new KnowledgeChunk(document, ChunkKind.ITEM, "Carrito de compras", null,
                "Carrito", new BigDecimal("16"), 2);

        assertThat(chunk.getRequirementType()).isEqualTo("Carrito");
        assertThat(chunk.getHours()).isEqualByComparingTo("16");
        assertThat(chunk.getOrdinal()).isEqualTo(2);
    }

    @Test
    void documentKindAndTextAreRequired() {
        assertThatThrownBy(() -> new KnowledgeChunk(null, ChunkKind.PASSAGE, "x", 1, null, null, 0))
                .hasMessageContaining("document");
        assertThatThrownBy(() -> new KnowledgeChunk(document, null, "x", 1, null, null, 0))
                .hasMessageContaining("kind");
        assertThatThrownBy(() -> new KnowledgeChunk(document, ChunkKind.PASSAGE, "  ", 1, null, null, 0))
                .hasMessageContaining("text");
    }
}
