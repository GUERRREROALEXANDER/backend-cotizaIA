package com.cotizaia.ingest.gmail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One row per Gmail message ever claimed: the idempotency ledger (spec 11). */
@Entity
@Table(name = "gmail_ingested_messages")
public class GmailIngestedMessage {

    public enum Status {
        CLAIMED, BRIEF_CREATED, PROCESSED, PROCESS_FAILED, FAILED, SKIPPED_UNKNOWN_SENDER, SKIPPED_EMPTY
    }

    @Id
    @Column(name = "message_id", length = 64)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;

    @Column(name = "brief_id")
    private Long briefId;

    @Column(length = 500)
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GmailIngestedMessage() {
    }

    public GmailIngestedMessage(String messageId, Instant now) {
        this.messageId = messageId;
        this.status = Status.CLAIMED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void mark(Status status, Long briefId, String error, Instant now) {
        this.status = status;
        if (briefId != null) {
            this.briefId = briefId;
        }
        // Only the exception class and a short message: never the email body.
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        this.updatedAt = now;
    }

    public String getMessageId() {
        return messageId;
    }

    public Status getStatus() {
        return status;
    }

    public Long getBriefId() {
        return briefId;
    }

    public String getError() {
        return error;
    }
}
