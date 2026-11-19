package com.cotizaia.mail;

import java.util.Arrays;
import java.util.List;

/**
 * One outgoing mail, independent of the transport (spec 16, Adapter pattern). The gateway port
 * receives this value object; it holds no token and no sender configuration, so the text is the
 * only thing a caller has to get right.
 *
 * @param to          recipient address visible to the person receiving it
 * @param subject     one short line; the no-op adapter logs exactly this and nothing else
 * @param text        plain-text body (no HTML templates, out of scope in spec 16)
 * @param attachments files mailed with the message; empty for plain notifications
 */
public record EmailMessage(String to, String subject, String text, List<Attachment> attachments) {

    public EmailMessage {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Recipient is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        text = text == null ? "" : text;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /** A message without attachments: the common case for the verification mail. */
    public EmailMessage(String to, String subject, String text) {
        this(to, subject, text, List.of());
    }

    /** One file of the message; {@code bytes} is the exact document payload. */
    public record Attachment(String name, String contentType, byte[] bytes) {

        public Attachment {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Attachment name is required");
            }
            if (contentType == null || contentType.isBlank()) {
                throw new IllegalArgumentException("Attachment content type is required");
            }
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Attachment bytes are required");
            }
            bytes = Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }
}
