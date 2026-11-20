package com.cotizaia.document;

/**
 * Visitor over the {@link DocumentNode} Composite. One method per concrete node type; the node picks the method in
 * {@link DocumentNode#accept}, so the operation knows the exact type without {@code instanceof} chains.
 *
 * @param <R> what the operation produces for each node
 */
public interface DocumentVisitor<R> {

    R visitSection(SectionNode section);

    R visitParagraph(ParagraphNode paragraph);

    R visitBulletList(BulletListNode list);
}
