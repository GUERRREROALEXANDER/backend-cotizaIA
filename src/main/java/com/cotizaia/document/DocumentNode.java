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

    /**
     * Visitor double dispatch: each node calls the visitor method for its own concrete type, so new operations over
     * the tree (Markdown export, statistics, ...) are added as visitors without touching the node classes.
     */
    <R> R accept(DocumentVisitor<R> visitor);
}
