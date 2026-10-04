package com.cotizaia.document;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import org.springframework.stereotype.Component;

/** Template Method ConcreteClass (project.txt section 6) presenting the quoted scope and price. */
@Component
public class ProposalDocumentGenerator extends AbstractDocumentTemplate {

    @Override
    public DocumentType type() {
        return DocumentType.PROPOSAL;
    }

    @Override
    protected String title() {
        return "Propuesta comercial";
    }

    @Override
    protected void writeSections(Document document, DocumentData data) throws DocumentException {
        if (data.summary() != null) {
            document.add(new Paragraph(data.summary()));
        }
        PdfPTable items = table("Requerimiento", "Horas", "Precio unitario", "Total");
        for (DocumentData.LineData line : data.lines()) {
            items.addCell(line.label());
            items.addCell(line.hours().toPlainString());
            items.addCell(money(line.unitPrice()));
            items.addCell(money(line.lineTotal()));
        }
        document.add(items);
        document.add(new Paragraph("Subtotal: " + money(data.subtotal())));
        for (DocumentData.ExtraData extra : data.extras()) {
            document.add(new Paragraph(extra.type() + ": " + money(extra.amount())));
        }
        document.add(new Paragraph("Total: " + money(data.total())));
        if (data.pricingModel() != null) {
            document.add(new Paragraph("Modelo de precios: " + data.pricingModel()));
        }
        document.add(new Paragraph("Anticipo (50%): " + money(data.depositAmount())));
    }
}
