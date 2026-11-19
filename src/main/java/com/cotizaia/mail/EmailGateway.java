package com.cotizaia.mail;

/**
 * Port of the mail transport (spec 16). Implementations must not leak delivery details into
 * callers that cannot act on them; {@link NoopEmailGateway} is the default, and
 * {@link SmtpEmailGateway} is only wired when {@code mail.enabled=true}.
 */
public interface EmailGateway {

    /** Sends the message, or throws when the transport refuses it. */
    void send(EmailMessage message);
}
