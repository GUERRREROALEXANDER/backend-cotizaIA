package com.cotizaia.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentNodeStructureTests {

    @Test
    void aSectionJoinsTheTextOfItsChildrenUnderItsTitle() {
        SectionNode section = new SectionNode("Alcance", List.of(
                new ParagraphNode("Cuatro páginas."),
                new BulletListNode(List.of("Inicio", "Contacto"))));

        assertThat(section.plainText()).isEqualTo("Alcance\nCuatro páginas.\nInicio\nContacto");
    }

    @Test
    void anUntitledSectionDoesNotStartWithABlankLine() {
        SectionNode root = new SectionNode(null, List.of(new ParagraphNode("Hola")));

        assertThat(root.title()).isEmpty();
        assertThat(root.plainText()).isEqualTo("Hola");
    }

    @Test
    void sectionsCanNestOtherSections() {
        SectionNode root = new SectionNode("", List.of(
                new SectionNode("Uno", List.of(new ParagraphNode("a"))),
                new SectionNode("Dos", List.of(new ParagraphNode("b")))));

        assertThat(root.plainText()).isEqualTo("Uno\na\nDos\nb");
    }

    @Test
    void childrenAndItemsAreCopiedSoTheTreeCannotChangeLater() {
        List<DocumentNode> children = new ArrayList<>(List.of(new ParagraphNode("a")));
        List<String> items = new ArrayList<>(List.of("x"));
        SectionNode section = new SectionNode("S", children);
        BulletListNode list = new BulletListNode(items);
        children.clear();
        items.clear();

        assertThat(section.children()).hasSize(1);
        assertThat(list.items()).containsExactly("x");
    }

    @Test
    void aSectionWithoutChildrenIsEmptyNotNull() {
        assertThat(new SectionNode("S", null).children()).isEmpty();
    }

    @Test
    void paragraphsAndListsRequireTheirContent() {
        assertThatThrownBy(() -> new ParagraphNode(null)).hasMessageContaining("paragraph text");
        assertThatThrownBy(() -> new BulletListNode(null)).hasMessageContaining("bullet items");
    }
}
