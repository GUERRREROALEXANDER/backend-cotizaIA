package com.cotizaia.api;

import com.cotizaia.domain.ProposalCommandRecord;
import java.time.Instant;

/** One executed command in the proposal's audit trail, oldest first. */
public record ProposalCommandResponse(String type, String description, Instant executedAt, Instant undoneAt) {

    public static ProposalCommandResponse from(ProposalCommandRecord record) {
        return new ProposalCommandResponse(record.getCommandType(), record.getDescription(),
                record.getExecutedAt(), record.getUndoneAt());
    }
}
