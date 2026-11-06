package com.cotizaia.domain;

import com.cotizaia.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persisted audit row of one executed {@code ProposalCommand}. The payload (the
 * data needed to rebuild the command) and the human description are encrypted at
 * rest because they may carry the client's requirement text.
 */
@Entity
@Table(name = "proposal_commands")
public class ProposalCommandRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @Column(name = "command_type", nullable = false, length = 40)
    private String commandType;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false)
    private String payload;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false)
    private String description;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "undone_at")
    private Instant undoneAt;

    protected ProposalCommandRecord() {
    }

    public ProposalCommandRecord(Proposal proposal, String commandType, String payload,
            String description, Instant executedAt) {
        if (proposal == null || commandType == null || commandType.isBlank()
                || payload == null || description == null || executedAt == null) {
            throw new IllegalArgumentException("proposal, type, payload, description and time are required");
        }
        this.proposal = proposal;
        this.commandType = commandType;
        this.payload = payload;
        this.description = description;
        this.executedAt = executedAt;
    }

    public void markUndone(Instant at) {
        if (at == null) {
            throw new IllegalArgumentException("undoneAt is required");
        }
        this.undoneAt = at;
    }

    public Long getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public String getCommandType() {
        return commandType;
    }

    public String getPayload() {
        return payload;
    }

    public String getDescription() {
        return description;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public Instant getUndoneAt() {
        return undoneAt;
    }
}
