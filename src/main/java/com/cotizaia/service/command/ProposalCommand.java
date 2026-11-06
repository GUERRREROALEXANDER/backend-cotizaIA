package com.cotizaia.service.command;

import com.cotizaia.domain.Proposal;

/**
 * Command pattern: every quote edit is an object that knows how to apply itself,
 * how to reverse itself, and how to describe itself for the audit trail.
 */
public interface ProposalCommand {

    void execute(Proposal proposal);

    void undo(Proposal proposal);

    String description();

    String type();

    String payloadJson();
}
