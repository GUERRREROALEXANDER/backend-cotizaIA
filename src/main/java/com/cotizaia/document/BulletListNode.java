package com.cotizaia.document;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.ListItem;
import java.util.List;

/** Composite leaf: an unordered list of short lines. */
public record BulletListNode(List<String> items) implements DocumentNode {

    public BulletListNode {
        if (items == null) {
            throw new IllegalArgumentException("bullet items are required");
        }
        items = List.copyOf(items);
    }

    @Override
    public void render(Document document) throws DocumentException {
        com.lowagie.text.List list = new com.lowagie.text.List();
        for (String item : items) {
            list.add(new ListItem(item));
        }
        document.add(list);
    }

    @Override
    public String plainText() {
        return String.join("\n", items);
    }
}
