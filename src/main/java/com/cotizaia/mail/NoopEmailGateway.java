package com.cotizaia.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link EmailGateway} while mail is off (spec 16). It drops the message and writes one
 * log line with the subject only: never the recipient, never the body, so a disabled deployment
 * leaves no address or verification link in its logs.
 */
public class NoopEmailGateway implements EmailGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoopEmailGateway.class);

    @Override
    public void send(EmailMessage message) {
        LOGGER.info("email skipped: {}", message.subject());
    }
}
