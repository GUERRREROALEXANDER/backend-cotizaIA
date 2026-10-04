package com.cotizaia.document;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import org.springframework.stereotype.Component;

/** Template Method ConcreteClass (project.txt section 6) presenting the project timeline. */
@Component
public class ScheduleDocumentGenerator extends AbstractDocumentTemplate {

    @Override
    public DocumentType type() {
        return DocumentType.SCHEDULE;
    }

    @Override
    protected String title() {
        return "Cronograma del proyecto";
    }

    @Override
    protected void writeSections(Document document, DocumentData data) throws DocumentException {
        PdfPTable phases = table("Fase", "Semanas", "Semana inicial", "Semana final");
        int totalWeeks = 0;
        for (DocumentData.PhaseData phase : data.phases()) {
            phases.addCell(phase.name());
            phases.addCell(Integer.toString(phase.weeks()));
            phases.addCell(Integer.toString(phase.startWeek()));
            phases.addCell(Integer.toString(phase.endWeek()));
            totalWeeks = Math.max(totalWeeks, phase.endWeek());
        }
        document.add(phases);
        document.add(new Paragraph("Semanas totales: " + totalWeeks));
    }
}
