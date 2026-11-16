package com.cotizaia.ingest.gmail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The history cursor (and watch expiry) of one mailbox. */
@Entity
@Table(name = "gmail_sync_state")
public class GmailSyncState {

    @Id
    @Column(length = 255)
    private String mailbox;

    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "watch_expires_at")
    private Instant watchExpiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GmailSyncState() {
    }

    public GmailSyncState(String mailbox, Instant now) {
        this.mailbox = mailbox;
        this.updatedAt = now;
    }

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId, Instant now) {
        this.historyId = historyId;
        this.updatedAt = now;
    }

    public Instant getWatchExpiresAt() {
        return watchExpiresAt;
    }

    public void setWatchExpiresAt(Instant watchExpiresAt, Instant now) {
        this.watchExpiresAt = watchExpiresAt;
        this.updatedAt = now;
    }
}
