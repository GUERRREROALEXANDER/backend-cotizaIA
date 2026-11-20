package com.cotizaia.document;

import java.util.regex.Pattern;

/**
 * Visitor that exports a drafted proposal to Markdown, so the agency can paste it into an email, a wiki or a
 * repository. Titled sections become headings whose level follows the nesting depth (capped at {@code ######});
 * the untitled root only groups its children.
 */
public final class MarkdownExportVisitor implements DocumentVisitor<String> {

    private static final int MAX_HEADING_LEVEL = 6;
    /**
     * Line starts that Markdown reads as structure: heading, quote, bullet, a setext underline ({@code ---} or
     * {@code ===}, which would turn the previous line into a heading) and a code fence. A backslash before the first
     * character keeps the line literal.
     */
    private static final Pattern BLOCK_START = Pattern.compile("^(?:[#>]|[+*-](?=\\s|$)|[=-]+\\s*$|`{3,}|~{3,})");
    /** Ordered-list marker; the escape goes before the dot, since a backslash before a digit is not an escape. */
    private static final Pattern ORDERED_START = Pattern.compile("^(\\d{1,9})([.)])(?=\\s|$)");

    private int depth;

    public static String export(DocumentNode root) {
        if (root == null) {
            throw new IllegalArgumentException("document root is required");
        }
        return root.accept(new MarkdownExportVisitor()).strip() + "\n";
    }

    @Override
    public String visitSection(SectionNode section) {
        StringBuilder markdown = new StringBuilder();
        boolean titled = !section.title().isBlank();
        if (titled) {
            depth++;
            markdown.append("#".repeat(Math.min(depth, MAX_HEADING_LEVEL))).append(' ')
                    .append(inline(section.title())).append("\n\n");
        }
        for (DocumentNode child : section.children()) {
            markdown.append(child.accept(this));
        }
        if (titled) {
            depth--;
        }
        return markdown.toString();
    }

    @Override
    public String visitParagraph(ParagraphNode paragraph) {
        return escapeBlock(paragraph.text()) + "\n\n";
    }

    @Override
    public String visitBulletList(BulletListNode list) {
        StringBuilder markdown = new StringBuilder();
        for (String item : list.items()) {
            markdown.append("- ").append(inline(item)).append('\n');
        }
        return markdown.append('\n').toString();
    }

    /** One line of text: newlines would otherwise turn the rest of a title or item into a new block. */
    private static String inline(String text) {
        return neutralizeHtml(text.strip().replaceAll("\\s*\\R\\s*", " "));
    }

    /**
     * Draft text comes from a model fed with client emails, so it is untrusted: raw HTML is neutralized and a line
     * that would start a heading, quote, list or ordered list is escaped to stay a plain paragraph line.
     */
    private static String escapeBlock(String text) {
        StringBuilder escaped = new StringBuilder();
        for (String line : neutralizeHtml(text.strip()).split("\\R", -1)) {
            // Leading indentation carries no meaning in a paragraph, but 4+ spaces after a blank line make a code block.
            String bare = line.stripLeading();
            String safe = ORDERED_START.matcher(bare).find()
                    ? ORDERED_START.matcher(bare).replaceFirst("$1\\\\$2")
                    : BLOCK_START.matcher(bare).replaceFirst("\\\\$0");
            escaped.append(safe).append('\n');
        }
        return escaped.toString().strip();
    }

    private static String neutralizeHtml(String text) {
        return text.replace("<", "&lt;");
    }
}
