package com.cotizaia.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.document.DocumentType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProposalDocumentEmailEventTests {

    @Test
    void sendingAProposalMailsTheProposalDocument() {
        ProposalDocumentEmailEvent event = ProposalDocumentEmailEvent.sent(7L, 2L);

        assertThat(event.documentType()).isEqualTo(DocumentType.PROPOSAL);
        assertThat(event.proposalId()).isEqualTo(7L);
        assertThat(event.actorUserId()).isEqualTo(2L);
        assertThat(event.depositAmount()).isNull();
        assertThat(event.paymentReference()).isNull();
    }

    @Test
    void acceptingMailsTheContractWithTheDeposit() {
        ProposalDocumentEmailEvent event = ProposalDocumentEmailEvent.accepted(7L, null,
                new BigDecimal("500000.00"), "SIM-1");

        assertThat(event.documentType()).isEqualTo(DocumentType.CONTRACT);
        assertThat(event.depositAmount()).isEqualByComparingTo("500000.00");
        assertThat(event.paymentReference()).isEqualTo("SIM-1");
        assertThat(event.actorUserId()).isNull();
    }
}
