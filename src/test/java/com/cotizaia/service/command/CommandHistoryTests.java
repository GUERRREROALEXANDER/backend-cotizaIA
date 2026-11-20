package com.cotizaia.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalCommandRecord;
import com.cotizaia.repository.ProposalCommandRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CommandHistoryTests {

    private final ProposalCommandRepository commands = mock(ProposalCommandRepository.class);
    private final CommandHistory history = new CommandHistory(commands, new ObjectMapper());
    private final Proposal proposal = mock(Proposal.class);

    @Test
    void runExecutesAndRecordsTheCommand() {
        AddExtraCommand command = new AddExtraCommand("URGENCY", new BigDecimal("0.25"), new BigDecimal("100.00"));

        history.run(proposal, command);

        verify(proposal).addExtra("URGENCY", new BigDecimal("0.25"), new BigDecimal("100.00"));
        ArgumentCaptor<ProposalCommandRecord> saved = ArgumentCaptor.forClass(ProposalCommandRecord.class);
        verify(commands).save(saved.capture());
        assertThat(saved.getValue().getCommandType()).isEqualTo("ADD_EXTRA");
        assertThat(saved.getValue().getPayload()).isEqualTo(command.payloadJson());
        assertThat(saved.getValue().getUndoneAt()).isNull();
    }

    @Test
    void undoLastRebuildsTheCommandFromItsPayloadAndMarksItUndone() {
        when(proposal.getId()).thenReturn(5L);
        RemoveExtraCommand removal = new RemoveExtraCommand(42L, "DISCOUNT", new BigDecimal("0.10"),
                new BigDecimal("-50.00"));
        ProposalCommandRecord record = new ProposalCommandRecord(proposal, removal.type(), removal.payloadJson(),
                removal.description(), Instant.parse("2026-11-20T10:00:00Z"));
        when(commands.findFirstByProposalIdAndUndoneAtIsNullOrderByExecutedAtDescIdDesc(5L))
                .thenReturn(Optional.of(record));

        history.undoLast(proposal);

        verify(proposal).addExtra("DISCOUNT", new BigDecimal("0.10"), new BigDecimal("-50.00"));
        assertThat(record.getUndoneAt()).isNotNull();
        verify(commands).save(record);
    }

    @Test
    void nothingToUndoIsAnError() {
        when(proposal.getId()).thenReturn(5L);
        when(commands.findFirstByProposalIdAndUndoneAtIsNullOrderByExecutedAtDescIdDesc(5L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> history.undoLast(proposal)).hasMessageContaining("No command left");
        verify(commands, never()).save(any());
    }

    @Test
    void anUnknownOrCorruptRecordIsNotUndone() {
        when(proposal.getId()).thenReturn(5L);
        ProposalCommandRecord unknown = new ProposalCommandRecord(proposal, "RENAME", "{}", "x",
                Instant.parse("2026-11-20T10:00:00Z"));
        when(commands.findFirstByProposalIdAndUndoneAtIsNullOrderByExecutedAtDescIdDesc(5L))
                .thenReturn(Optional.of(unknown));

        assertThatThrownBy(() -> history.undoLast(proposal)).hasMessageContaining("Corrupt command payload");
        assertThat(unknown.getUndoneAt()).isNull();
    }
}
