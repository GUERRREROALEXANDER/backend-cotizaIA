package com.cotizaia.document;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;

/**
 * Composite component (project.txt section 6): paragraphs, bullet lists and whole sections share one
 * rendering contract, so a drafted proposal renders through a single call on its root node.
 */
public interface DocumentNode {

    void render(Document document) throws DocumentException;

    String plainText();
}
