package com.cotizaia.service.command;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalCommandRecord;
import com.cotizaia.repository.ProposalCommandRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command history (Command pattern): executes an edit and records it in one
 * step, and can undo the latest not-yet-undone edit by rebuilding it from its
 * type plus payload.
 */
@Service
public class CommandHistory {

    private final ProposalCommandRepository commands;

    private final ObjectMapper json;

    public CommandHistory(ProposalCommandRepository commands, ObjectMapper json) {
        this.commands = commands;
        this.json = json;
    }

    @Transactional
    public void run(Proposal proposal, ProposalCommand command) {
        command.execute(proposal);
        commands.save(new ProposalCommandRecord(proposal, command.type(), command.payloadJson(),
                command.description(), Instant.now()));
    }

    @Transactional
    public void undoLast(Proposal proposal) {
        ProposalCommandRecord record = commands
                .findFirstByProposalIdAndUndoneAtIsNullOrderByExecutedAtDescIdDesc(proposal.getId())
                .orElseThrow(() -> new IllegalStateException("No command left to undo for this proposal"));
        rebuild(record).undo(proposal);
        record.markUndone(Instant.now());
        commands.save(record);
    }

    @Transactional(readOnly = true)
    public List<ProposalCommandRecord> list(Long proposalId) {
        return commands.findByProposalIdOrderByExecutedAtAscIdAsc(proposalId);
    }

    private ProposalCommand rebuild(ProposalCommandRecord record) {
        if (!AdjustHoursCommand.TYPE.equals(record.getCommandType())) {
            throw new IllegalStateException("Unknown command type: " + record.getCommandType());
        }
        try {
            JsonNode payload = json.readTree(record.getPayload());
            return new AdjustHoursCommand(payload.get("requirementId").asLong(),
                    new BigDecimal(payload.get("before").asText()),
                    new BigDecimal(payload.get("after").asText()));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Corrupt command payload for record " + record.getId(), exception);
        }
    }
}
