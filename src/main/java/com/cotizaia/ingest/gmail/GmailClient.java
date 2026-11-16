package com.cotizaia.ingest.gmail;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Port to the Gmail API. Only the calls the intake needs, all read-only except
 * {@link #watch}, which registers the Pub/Sub notification (gmail.readonly is
 * enough for it).
 */
public interface GmailClient {

    /** Resolves a label name such as "cotizar" to its Gmail id (Label_123...). */
    String labelId(String labelName);

    /** The mailbox's current historyId, used to start a fresh cursor. */
    long currentHistoryId();

    /**
     * Message ids added to the mailbox or labelled with {@code labelId} since
     * {@code startHistoryId} (users.history.list, every page).
     *
     * @throws HistoryExpiredException when Gmail answers 404: the cursor is
     *         older than the history Gmail keeps (about a week)
     */
    HistoryPage history(long startHistoryId, String labelId);

    /** Message ids matching a Gmail search query (users.messages.list). */
    List<String> search(String query, int max);

    /** The full message resource (users.messages.get, format=full). */
    Map<String, Object> message(String id);

    /** Starts or renews the push watch on one label (users.watch). */
    Watch watch(String topic, String labelId);

    record HistoryPage(List<String> messageIds, long historyId) {
    }

    record Watch(long historyId, Instant expiresAt) {
    }

    class HistoryExpiredException extends RuntimeException {
        public HistoryExpiredException(String message) {
            super(message);
        }
    }
}
