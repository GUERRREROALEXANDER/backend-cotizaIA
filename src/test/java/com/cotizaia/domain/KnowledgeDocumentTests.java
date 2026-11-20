package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KnowledgeDocumentTests {

    private final Agency agency = new Agency("Estudio Nariño Digital");

    @Test
    void anUploadedPdfKeepsItsNameAndKind() {
        KnowledgeDocument document = new KnowledgeDocument(agency, "Propuesta Café Galeras.pdf",
                KnowledgeDocumentKind.PROPOSAL_PDF);
        document.setPageCount(6);

        assertThat(document.getName()).isEqualTo("Propuesta Café Galeras.pdf");
        assertThat(document.getKind()).isEqualTo(KnowledgeDocumentKind.PROPOSAL_PDF);
        assertThat(document.getPageCount()).isEqualTo(6);
        assertThat(document.getSourceProposalId()).isNull();
    }

    @Test
    void anApprovedProposalRemembersWhereItCameFrom() {
        KnowledgeDocument document = new KnowledgeDocument(agency, "Propuesta #7",
                KnowledgeDocumentKind.APPROVED_PROPOSAL);
        document.setSourceProposalId(7L);

        assertThat(document.getSourceProposalId()).isEqualTo(7L);
    }

    @Test
    void theCreationTimeIsSetOnceWhenSaved() {
        KnowledgeDocument document = new KnowledgeDocument(agency, "P.pdf", KnowledgeDocumentKind.PROPOSAL_PDF);

        document.onCreate();
        var first = document.getCreatedAt();
        document.onCreate();

        assertThat(first).isNotNull();
        assertThat(document.getCreatedAt()).isEqualTo(first);
    }

    @Test
    void agencyNameAndKindAreRequired() {
        assertThatThrownBy(() -> new KnowledgeDocument(null, "P.pdf", KnowledgeDocumentKind.PROPOSAL_PDF))
                .hasMessageContaining("agency");
        assertThatThrownBy(() -> new KnowledgeDocument(agency, " ", KnowledgeDocumentKind.PROPOSAL_PDF))
                .hasMessageContaining("name");
        assertThatThrownBy(() -> new KnowledgeDocument(agency, "P.pdf", null)).hasMessageContaining("kind");
    }
}
