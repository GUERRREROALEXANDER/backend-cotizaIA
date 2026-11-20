package com.cotizaia.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DraftDocumentBuilderTests {

    private static ProposalDraft draft(List<SourceRef> sources) {
        return new ProposalDraft("Resumen",
                List.of(new DraftSection("Alcance", List.of(
                        new DraftParagraph("Cuatro páginas con SEO básico.", List.of(1, 2)),
                        new DraftParagraph("Botón de WhatsApp para pedidos.", List.of())))),
                sources, DraftOrigin.AI);
    }

    @Test
    void sectionsAndParagraphsBecomeATree() {
        SectionNode root = (SectionNode) DraftDocumentBuilder.build(draft(List.of()));

        assertThat(root.title()).isEmpty();
        assertThat(root.children()).singleElement().isInstanceOfSatisfying(SectionNode.class, section -> {
            assertThat(section.title()).isEqualTo("Alcance");
            assertThat(section.children()).hasSize(2).allMatch(ParagraphNode.class::isInstance);
        });
    }

    @Test
    void citedSourcesBecomeNumberedMarkers() {
        SectionNode root = (SectionNode) DraftDocumentBuilder.build(draft(List.of()));
        SectionNode scope = (SectionNode) root.children().get(0);

        assertThat(((ParagraphNode) scope.children().get(0)).text())
                .isEqualTo("Cuatro páginas con SEO básico. [1] [2]");
        assertThat(((ParagraphNode) scope.children().get(1)).text()).isEqualTo("Botón de WhatsApp para pedidos.");
    }

    @Test
    void sourcesGetTheirOwnListAtTheEnd() {
        SectionNode root = (SectionNode) DraftDocumentBuilder.build(draft(List.of(
                new SourceRef(1, "Propuesta Café Galeras.pdf", 3),
                new SourceRef(2, "Catálogo", null))));

        assertThat(root.children()).hasSize(2);
        SectionNode sources = (SectionNode) root.children().get(1);
        assertThat(sources.title()).isEqualTo("Fuentes");
        assertThat(((BulletListNode) sources.children().get(0)).items())
                .containsExactly("[1] Propuesta Café Galeras.pdf, p. 3", "[2] Catálogo, p. -");
    }

    @Test
    void withoutSourcesThereIsNoSourcesSection() {
        SectionNode root = (SectionNode) DraftDocumentBuilder.build(draft(List.of()));

        assertThat(root.children()).noneMatch(node -> node instanceof SectionNode section
                && section.title().equals("Fuentes"));
    }

    @Test
    void aDraftIsRequired() {
        assertThatThrownBy(() -> DraftDocumentBuilder.build(null)).hasMessageContaining("draft is required");
    }
}
