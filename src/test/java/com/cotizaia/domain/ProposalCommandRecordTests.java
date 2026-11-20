package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProposalCommandRecordTests {

    private static final Instant AT = Instant.parse("2026-11-20T10:00:00Z");

    private final Proposal proposal = mock(Proposal.class);

    @Test
    void aNewRecordIsNotUndone() {
        ProposalCommandRecord record = new ProposalCommandRecord(proposal, "ADD_EXTRA", "{}", "Extra URGENCY", AT);

        assertThat(record.getUndoneAt()).isNull();
        assertThat(record.getCommandType()).isEqualTo("ADD_EXTRA");
        assertThat(record.getExecutedAt()).isEqualTo(AT);
    }

    @Test
    void markingUndoneKeepsTheTime() {
        ProposalCommandRecord record = new ProposalCommandRecord(proposal, "ADD_EXTRA", "{}", "Extra", AT);

        record.markUndone(AT.plusSeconds(30));

        assertThat(record.getUndoneAt()).isEqualTo(AT.plusSeconds(30));
        assertThatThrownBy(() -> record.markUndone(null)).hasMessageContaining("undoneAt");
    }

    @Test
    void everyPartIsRequired() {
        assertThatThrownBy(() -> new ProposalCommandRecord(null, "T", "{}", "d", AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProposalCommandRecord(proposal, " ", "{}", "d", AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProposalCommandRecord(proposal, "T", null, "d", AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProposalCommandRecord(proposal, "T", "{}", null, AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProposalCommandRecord(proposal, "T", "{}", "d", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
