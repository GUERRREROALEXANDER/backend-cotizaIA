package com.cotizaia.document;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visitor that measures a drafted proposal: sections, paragraphs, bullet items, words, distinct cited sources and an
 * estimated reading time. Reviewers use it to spot drafts that are too thin or that cite no evidence at all.
 */
public final class DocumentStatsVisitor implements DocumentVisitor<Void> {

    /** Average adult reading speed used for the estimate, in words per minute. */
    static final int WORDS_PER_MINUTE = 200;

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’.-]*");

    private int sections;
    private int paragraphs;
    private int bulletItems;
    private int words;
    private final Set<Integer> citedSources = new TreeSet<>();

    public static DocumentStats measure(DocumentNode root) {
        if (root == null) {
            throw new IllegalArgumentException("document root is required");
        }
        DocumentStatsVisitor visitor = new DocumentStatsVisitor();
        root.accept(visitor);
        return visitor.result();
    }

    @Override
    public Void visitSection(SectionNode section) {
        if (!section.title().isBlank()) {
            sections++;
            words += countWords(section.title());
        }
        for (DocumentNode child : section.children()) {
            child.accept(this);
        }
        return null;
    }

    @Override
    public Void visitParagraph(ParagraphNode paragraph) {
        paragraphs++;
        Matcher citation = CITATION.matcher(paragraph.text());
        while (citation.find()) {
            citedSources.add(Integer.parseInt(citation.group(1)));
        }
        words += countWords(CITATION.matcher(paragraph.text()).replaceAll(" "));
        return null;
    }

    @Override
    public Void visitBulletList(BulletListNode list) {
        bulletItems += list.items().size();
        for (String item : list.items()) {
            words += countWords(item);
        }
        return null;
    }

    private DocumentStats result() {
        int minutes = words == 0 ? 0 : Math.max(1, (int) Math.ceil(words / (double) WORDS_PER_MINUTE));
        return new DocumentStats(sections, paragraphs, bulletItems, words, citedSources.size(), minutes);
    }

    private static int countWords(String text) {
        int count = 0;
        Matcher word = WORD.matcher(text);
        while (word.find()) {
            count++;
        }
        return count;
    }
}
