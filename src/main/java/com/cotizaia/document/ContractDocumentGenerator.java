package com.cotizaia.document;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import org.springframework.stereotype.Component;

/** Template Method ConcreteClass (project.txt section 6) presenting the parties and agreed clauses. */
@Component
public class ContractDocumentGenerator extends AbstractDocumentTemplate {

    @Override
    public DocumentType type() {
        return DocumentType.CONTRACT;
    }

    @Override
    protected String title() {
        return "Contrato de prestacion de servicios";
    }

    @Override
    protected void writeSections(Document document, DocumentData data) throws DocumentException {
        document.add(new Paragraph("Proveedor: " + data.agencyName()));
        document.add(new Paragraph("Cliente: " + data.clientName()));
        for (int index = 0; index < data.clauses().size(); index++) {
            document.add(new Paragraph((index + 1) + ". " + data.clauses().get(index)));
        }
        document.add(new Paragraph("Valor: " + money(data.total())));
        document.add(new Paragraph("Anticipo (50%): " + money(data.depositAmount())));
    }
}
