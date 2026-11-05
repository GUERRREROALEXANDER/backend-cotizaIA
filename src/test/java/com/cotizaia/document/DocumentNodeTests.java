package com.cotizaia.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Composite: sections, paragraphs and bullet lists are all {@link DocumentNode}s, so a whole drafted proposal
 * renders (and prints as text) through one call on its root, however deep the nesting.
 */
class DocumentNodeTests {

    @Test
    void aCompositeRendersItsChildrenInOrderThroughOneInterface() throws Exception {
        DocumentNode root = new SectionNode("Alcance", List.of(
                new ParagraphNode("Tienda en linea para el catalogo."),
                new SectionNode("Detalle", List.of(new BulletListNode(List.of("Carrito", "Buscador")))),
                new ParagraphNode("Pasarela de pagos.")));

        String text = pdfText(root);

        assertThat(text).containsSubsequence("Alcance", "Tienda en linea para el catalogo.", "Detalle", "Carrito",
                "Buscador", "Pasarela de pagos.");
        assertThat(root.plainText()).containsSubsequence("Alcance", "Detalle", "Carrito", "Pasarela de pagos.");
    }

    @Test
    void leavesAndCompositesAreInterchangeable() throws Exception {
        List<DocumentNode> nodes = List.of(new ParagraphNode("solo"), new BulletListNode(List.of("a", "b")),
                new SectionNode("vacia", List.of()));
        for (DocumentNode node : nodes) {
            assertThat(pdfText(node)).isNotBlank();
        }
    }

    @Test
    void draftBuilderAddsSourceMarkersAndASourcesSection() {
        ProposalDraft draft = new ProposalDraft("Resumen", List.of(
                new DraftSection("Alcance", List.of(new DraftParagraph("Tienda con carrito.", List.of(1, 2)))),
                new DraftSection("Supuestos", List.of(new DraftParagraph("El cliente entrega fotos.", List.of(2))))),
                List.of(new SourceRef(1, "P21-propuesta.pdf", 1), new SourceRef(2, "P08-propuesta.pdf", 2)),
                DraftOrigin.AI);

        String text = DraftDocumentBuilder.build(draft).plainText();

        assertThat(text).contains("Tienda con carrito. [1] [2]").contains("El cliente entrega fotos. [2]")
                .containsSubsequence("Fuentes", "[1] P21-propuesta.pdf, p. 1", "[2] P08-propuesta.pdf, p. 2");
    }

    @Test
    void draftSurvivesAJsonRoundTrip() {
        ProposalDraft draft = new ProposalDraft("Resumen", List.of(new DraftSection("Alcance",
                List.of(new DraftParagraph("Texto", List.of(1))))), List.of(new SourceRef(1, "P1.pdf", null)),
                DraftOrigin.TEMPLATE);
        assertThat(ProposalDraft.fromJson(draft.toJson())).isEqualTo(draft);
    }

    private String pdfText(DocumentNode node) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();
        node.render(document);
        document.close();
        try (PDDocument pdf = Loader.loadPDF(out.toByteArray())) {
            return new PDFTextStripper().getText(pdf);
        }
    }
}
