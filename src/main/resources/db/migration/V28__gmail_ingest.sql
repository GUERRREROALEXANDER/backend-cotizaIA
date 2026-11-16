--
-- Spec 11: automatic email intake from the cotiza@ Gmail mailbox.
--
-- gmail_ingested_messages is the idempotency ledger: the Gmail message id is
-- the primary key, so a message reached twice (poll + push, or a Pub/Sub
-- redelivery) is claimed once. A row is written BEFORE the brief, so a crash
-- between the two leaves a FAILED/CLAIMED row instead of a duplicate brief.
--
-- gmail_sync_state keeps the history cursor per mailbox, so a restart resumes
-- from the last seen historyId instead of re-reading the inbox.
--
CREATE TABLE gmail_ingested_messages (
    message_id   VARCHAR(64)  NOT NULL PRIMARY KEY,
    status       VARCHAR(24)  NOT NULL,
    brief_id     BIGINT       NULL,
    error        VARCHAR(500) NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT fk_gmail_ingested_brief FOREIGN KEY (brief_id) REFERENCES briefs (id) ON DELETE SET NULL
);

CREATE TABLE gmail_sync_state (
    mailbox            VARCHAR(255) NOT NULL PRIMARY KEY,
    history_id         BIGINT       NULL,
    watch_expires_at   TIMESTAMP    NULL,
    updated_at         TIMESTAMP    NOT NULL
);
