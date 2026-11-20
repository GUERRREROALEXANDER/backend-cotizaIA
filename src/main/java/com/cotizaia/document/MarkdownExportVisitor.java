package com.cotizaia.document;

/**
 * Visitor that exports a drafted proposal to Markdown, so the agency can paste it into an email, a wiki or a
 * repository. Titled sections become headings whose level follows the nesting depth (capped at {@code ######});
 * the untitled root only groups its children.
 */
public final class MarkdownExportVisitor implements DocumentVisitor<String> {

    private static final int MAX_HEADING_LEVEL = 6;

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
                    .append(section.title().strip()).append("\n\n");
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
        return paragraph.text().strip() + "\n\n";
    }

    @Override
    public String visitBulletList(BulletListNode list) {
        StringBuilder markdown = new StringBuilder();
        for (String item : list.items()) {
            markdown.append("- ").append(item.strip()).append('\n');
        }
        return markdown.append('\n').toString();
    }
}
