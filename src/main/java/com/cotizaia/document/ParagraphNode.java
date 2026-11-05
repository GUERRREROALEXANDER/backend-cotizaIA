package com.cotizaia.document;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;

/** Composite leaf: one body paragraph. */
public record ParagraphNode(String text) implements DocumentNode {

    public ParagraphNode {
        if (text == null) {
            throw new IllegalArgumentException("paragraph text is required");
        }
    }

    @Override
    public void render(Document document) throws DocumentException {
        document.add(new Paragraph(text));
    }

    @Override
    public String plainText() {
        return text;
    }
}
