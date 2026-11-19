package com.cotizaia.mail;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Spec 16: with mail off the no-op adapter sends nothing and the only trace is one log line with the
 * subject, so a disabled deployment never writes a recipient address or a verification link to disk.
 */
class NoopEmailGatewayTests {

    @Test
    void dropsTheMessageAndLogsOnlyTheSubject() {
        Logger logger = (Logger) LoggerFactory.getLogger(NoopEmailGateway.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new NoopEmailGateway().send(new EmailMessage("cliente@example.com", "Propuesta enviada: PDF adjunto",
                    "Hola, cuerpo con el token 1234 y el total $1", java.util.List.of(
                            new EmailMessage.Attachment("propuesta-1.pdf", "application/pdf", new byte[] {1, 2}))));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        String line = appender.list.get(0).getFormattedMessage();
        assertThat(line).isEqualTo("email skipped: Propuesta enviada: PDF adjunto");
        assertThat(line).doesNotContain("cliente@example.com");
        assertThat(line).doesNotContain("token");
    }
}
