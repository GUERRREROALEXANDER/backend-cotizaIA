package com.cotizaia.document;

/** Measurements of a drafted proposal produced by {@link DocumentStatsVisitor}. */
public record DocumentStats(int sections, int paragraphs, int bulletItems, int words, int citedSources,
        int readingMinutes) {
}
