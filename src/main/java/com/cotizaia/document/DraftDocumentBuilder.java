package com.cotizaia.document;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Composite tree for a drafted proposal: every draft section becomes a {@link SectionNode},
 * each paragraph carries its source markers, and the cited passages collapse into one "Fuentes" list.
 */
public final class DraftDocumentBuilder {

    private DraftDocumentBuilder() {
    }

    public static DocumentNode build(ProposalDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft is required");
        }
        List<DocumentNode> sections = new ArrayList<>();
        for (DraftSection section : draft.sections()) {
            List<DocumentNode> paragraphs = new ArrayList<>();
            for (DraftParagraph paragraph : section.paragraphs()) {
                paragraphs.add(new ParagraphNode(withMarkers(paragraph)));
            }
            sections.add(new SectionNode(section.title(), paragraphs));
        }
        if (!draft.sources().isEmpty()) {
            List<String> lines = draft.sources().stream().map(DraftDocumentBuilder::sourceLine).toList();
            sections.add(new SectionNode("Fuentes", List.of(new BulletListNode(lines))));
        }
        return new SectionNode("", sections);
    }

    private static String withMarkers(DraftParagraph paragraph) {
        StringBuilder text = new StringBuilder(paragraph.text());
        for (Integer source : paragraph.sources()) {
            text.append(" [").append(source).append(']');
        }
        return text.toString();
    }

    private static String sourceLine(SourceRef source) {
        String page = source.page() == null ? "-" : source.page().toString();
        return "[" + source.number() + "] " + source.documentName() + ", p. " + page;
    }
}
