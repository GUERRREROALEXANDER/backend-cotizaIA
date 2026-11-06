package com.cotizaia.ingest;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;

/**
 * Builds real PDF bytes for upload tests. Each argument is one page; an empty
 * string produces a page with no text, which is how a scanned page looks to a
 * text extractor.
 */
public final class PdfFixtures {

    private PdfFixtures() {
    }

    public static byte[] pdf(String... pages) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.open();
        for (int i = 0; i < pages.length; i++) {
            if (i > 0) {
                document.newPage();
            }
            if (pages[i].isEmpty()) {
                // Keep the blank page instead of letting OpenPDF drop it.
                writer.setPageEmpty(false);
            } else {
                document.add(new Paragraph(pages[i]));
            }
        }
        document.close();
        return out.toByteArray();
    }

    public static String[] numberedPages(int count) {
        String[] pages = new String[count];
        for (int i = 0; i < count; i++) {
            pages[i] = "Pagina " + (i + 1);
        }
        return pages;
    }
}
