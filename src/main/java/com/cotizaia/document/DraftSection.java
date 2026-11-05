package com.cotizaia.document;

import java.util.List;

/** One titled block of a drafted proposal. */
public record DraftSection(String title, List<DraftParagraph> paragraphs) {

    public DraftSection {
        title = title == null ? "" : title;
        paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
    }
}
