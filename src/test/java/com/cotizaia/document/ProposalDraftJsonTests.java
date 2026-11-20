package com.cotizaia.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProposalDraftJsonTests {

    @Test
    void blankJsonMeansNoDraft() {
        assertThat(ProposalDraft.fromJson(null)).isNull();
        assertThat(ProposalDraft.fromJson("  ")).isNull();
    }

    @Test
    void brokenJsonIsRejected() {
        assertThatThrownBy(() -> ProposalDraft.fromJson("{not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decode");
    }

    @Test
    void missingPartsGetSafeDefaults() {
        ProposalDraft draft = new ProposalDraft(null, null, null, null);

        assertThat(draft.sections()).isEmpty();
        assertThat(draft.sources()).isEmpty();
        assertThat(draft.origin()).isEqualTo(DraftOrigin.TEMPLATE);
    }

    @Test
    void sectionsAndParagraphsDefaultToEmptyValues() {
        DraftSection section = new DraftSection(null, null);
        DraftParagraph paragraph = new DraftParagraph(null, null);

        assertThat(section.title()).isEmpty();
        assertThat(section.paragraphs()).isEmpty();
        assertThat(paragraph.text()).isEmpty();
        assertThat(paragraph.sources()).isEmpty();
    }
}
