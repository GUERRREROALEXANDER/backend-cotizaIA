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

    private static final int WORDS_IN_FIXTURE = 20; // 3 titles + 12 paragraph words + 5 source-line words (markers excluded)

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
        assertThat(stats.words()).as("exact, deterministic count").isEqualTo(WORDS_IN_FIXTURE);
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

    @Test
    void anOversizedCitationMarkerDoesNotBreakTheStats() {
        DocumentStats stats = DocumentStatsVisitor.measure(new ParagraphNode("Texto [99999999999] y [3]"));

        assertThat(stats.citedSources()).isEqualTo(1);
    }

    @Test
    void bulletItemsCountTheirCitationsAndDropTheirMarkersFromTheWordCount() {
        DocumentStats stats = DocumentStatsVisitor.measure(new BulletListNode(List.of("[1] Fuente uno", "[2] Fuente dos")));

        assertThat(stats.citedSources()).isEqualTo(2);
        assertThat(stats.words()).isEqualTo(4);
    }

    @Test
    void exportedTextCannotInjectHeadingsListsOrHtml() {
        String markdown = MarkdownExportVisitor.export(new SectionNode("", List.of(
                new ParagraphNode("# Aprobado\n- pago aqui\n1. otro\n<script>x</script>\n**negrita** normal"),
                new BulletListNode(List.of("linea uno\n# colada")))));

        assertThat(markdown).isEqualTo(
                "\\# Aprobado\n\\- pago aqui\n1\\. otro\n&lt;script>x&lt;/script>\n**negrita** normal\n\n- linea uno # colada\n");
    }

    @Test
    void setextUnderlinesCodeFencesAndIndentationStayPlainText() {
        String markdown = MarkdownExportVisitor.export(new ParagraphNode(
                "Titulo falso\n---\nOtro\n===\n```js\n~~~\n2) paso\n\n    codigo indentado\n-5% de descuento"));

        assertThat(markdown).isEqualTo("Titulo falso\n\\---\nOtro\n\\===\n\\```js\n\\~~~\n2\\) paso\n\ncodigo indentado\n"
                + "-5% de descuento\n");
    }
}
