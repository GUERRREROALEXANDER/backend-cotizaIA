package com.cotizaia.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Visitor over the document Composite: each node dispatches to the method for its own type, and new operations
 * (Markdown export, statistics) work on the same tree the PDF is rendered from.
 */
class DocumentVisitorTests {

    private final ProposalDraft draft = new ProposalDraft("Web para restaurante",
            List.of(new DraftSection("Alcance", List.of(
                            new DraftParagraph("Diseno responsive con menu digital.", List.of(1)),
                            new DraftParagraph("Sistema de reservas en linea.", List.of(1, 2)))),
                    new DraftSection("Entregables", List.of(new DraftParagraph("Sitio publicado.", List.of())))),
            List.of(new SourceRef(1, "propuesta-2025.pdf", 3), new SourceRef(2, "catalogo.pdf", null)),
            DraftOrigin.AI);

    @Test
    void eachNodeCallsTheVisitorMethodForItsOwnType() {
        List<String> visited = new ArrayList<>();
        DocumentVisitor<Void> recorder = new DocumentVisitor<>() {
            @Override
            public Void visitSection(SectionNode section) {
                visited.add("section:" + section.title());
                section.children().forEach(child -> child.accept(this));
                return null;
            }

            @Override
            public Void visitParagraph(ParagraphNode paragraph) {
                visited.add("paragraph");
                return null;
            }

            @Override
            public Void visitBulletList(BulletListNode list) {
                visited.add("bullets:" + list.items().size());
                return null;
            }
        };

        DraftDocumentBuilder.build(draft).accept(recorder);

        assertThat(visited).containsExactly("section:", "section:Alcance", "paragraph", "paragraph",
                "section:Entregables", "paragraph", "section:Fuentes", "bullets:2");
    }

    @Test
    void exportsTheDraftToMarkdownWithHeadingsParagraphsAndSourceList() {
        String markdown = MarkdownExportVisitor.export(DraftDocumentBuilder.build(draft));

        assertThat(markdown).isEqualTo("""
                # Alcance

                Diseno responsive con menu digital. [1]

                Sistema de reservas en linea. [1] [2]

                # Entregables

                Sitio publicado.

                # Fuentes

                - [1] propuesta-2025.pdf, p. 3
                - [2] catalogo.pdf, p. -
                """);
    }

    @Test
    void nestedSectionsGetDeeperHeadings() {
        DocumentNode tree = new SectionNode("", List.of(new SectionNode("Fase 1",
                List.of(new SectionNode("Diseno", List.of(new ParagraphNode("Wireframes.")))))));

        assertThat(MarkdownExportVisitor.export(tree)).isEqualTo("# Fase 1\n\n## Diseno\n\nWireframes.\n");
    }

    @Test
    void measuresSectionsWordsCitationsAndReadingTime() {
        DocumentStats stats = DocumentStatsVisitor.measure(DraftDocumentBuilder.build(draft));

        assertThat(stats.sections()).isEqualTo(3);
        assertThat(stats.paragraphs()).isEqualTo(3);
        assertThat(stats.bulletItems()).isEqualTo(2);
        assertThat(stats.citedSources()).as("distinct [n] markers").isEqualTo(2);
        assertThat(stats.words()).isGreaterThan(10);
        assertThat(stats.readingMinutes()).isEqualTo(1);
    }

    @Test
    void citationMarkersAreNotCountedAsWords() {
        DocumentStats stats = DocumentStatsVisitor.measure(new ParagraphNode("Dos palabras [1] [2]"));

        assertThat(stats.words()).isEqualTo(2);
        assertThat(stats.citedSources()).isEqualTo(2);
    }

    @Test
    void anEmptyDocumentTakesNoTimeToRead() {
        DocumentStats stats = DocumentStatsVisitor.measure(new SectionNode("", List.of()));

        assertThat(stats).isEqualTo(new DocumentStats(0, 0, 0, 0, 0, 0));
    }
}
