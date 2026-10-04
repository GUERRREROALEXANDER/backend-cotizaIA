package com.cotizaia.document;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Template Method AbstractClass (project.txt section 6): fixes common PDF framing so all documents carry
 * the same agency identity and draft disclaimer; subclasses supply only their content sections.
 */
public abstract class AbstractDocumentTemplate {

    public final byte[] render(DocumentData data) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(document, output);
            document.open();
            writeHeader(document, data);
            writeSections(document, data);
            writeFooter(document);
        } catch (DocumentException exception) {
            throw new IllegalStateException("PDF generation failed", exception);
        } finally {
            document.close();
        }
        return output.toByteArray();
    }

    public abstract DocumentType type();

    protected abstract String title();

    protected abstract void writeSections(Document document, DocumentData data) throws DocumentException;

    private void writeHeader(Document document, DocumentData data) throws DocumentException {
        document.add(new Paragraph(data.agencyName(), new Font(Font.HELVETICA, 18, Font.BOLD)));
        document.add(new Paragraph(title(), new Font(Font.HELVETICA, 15, Font.BOLD)));
        document.add(new Paragraph(data.reference() + " | " + data.clientName() + " | " + data.issuedOn()));
        document.add(new Paragraph("____________________________________________________________"));
    }

    private void writeFooter(Document document) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Documento generado por CotizaIA - borrador sujeto a aprobacion humana. "
                + "Pagos simulados: no se mueve dinero real.", new Font(Font.HELVETICA, 9)));
    }

    protected final PdfPTable table(String... headings) {
        PdfPTable table = new PdfPTable(headings.length);
        table.setWidthPercentage(100);
        for (String heading : headings) {
            table.addCell(heading);
        }
        return table;
    }

    protected final String money(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return "$ " + formatter.format(amount);
    }
}
