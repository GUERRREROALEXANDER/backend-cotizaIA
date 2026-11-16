package com.cotizaia.ingest.gmail;

import java.time.Instant;
import java.util.List;

/**
 * The parts of a Gmail message the intake uses, already decoded.
 *
 * @param id Gmail message id (the idempotency key)
 * @param messageIdHeader RFC 822 Message-ID, kept for audit
 * @param fromAddress sender address, lower case
 * @param fromName sender display name, or the address when there is none
 * @param subject subject line, may be empty
 * @param body plain-text body, quoted reply trimmed, capped
 * @param truncated true when the body was cut at the cap
 * @param receivedAt Gmail internalDate
 * @param labelIds labels on the message
 */
public record GmailMessage(String id, String messageIdHeader, String fromAddress, String fromName, String subject,
        String body, boolean truncated, Instant receivedAt, List<String> labelIds) {
}
