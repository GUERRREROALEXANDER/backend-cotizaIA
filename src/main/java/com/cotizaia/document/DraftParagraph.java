package com.cotizaia.document;

import java.util.List;

/** One drafted paragraph plus the 1-based source numbers it cites. */
public record DraftParagraph(String text, List<Integer> sources) {

    public DraftParagraph {
        text = text == null ? "" : text;
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
