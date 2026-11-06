package com.cotizaia.document;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import java.util.List;

/**
 * Composite container: renders its title and then every child in order. Children may themselves be sections,
 * so nesting is transparent to callers that only know the {@link DocumentNode} interface.
 */
public record SectionNode(String title, List<DocumentNode> children) implements DocumentNode {

    public SectionNode {
        title = title == null ? "" : title;
        children = children == null ? List.of() : List.copyOf(children);
    }

    @Override
    public void render(Document document) throws DocumentException {
        if (!title.isBlank()) {
            document.add(new Paragraph(title, new Font(Font.HELVETICA, 13, Font.BOLD)));
        }
        for (DocumentNode child : children) {
            child.render(document);
        }
    }

    @Override
    public String plainText() {
        StringBuilder text = new StringBuilder(title);
        for (DocumentNode child : children) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(child.plainText());
        }
        return text.toString();
    }
}
