package com.cotizaia.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Spec 16: the SMTP adapter is the only class that talks to Spring Mail. It must carry recipient,
 * sender, subject and body, attach the exact bytes with their file name and content type, and let a
 * transport failure escape as an exception (the observers are the ones that swallow it).
 */
class SmtpEmailGatewayTests {

    private final CapturingMailSender sender = new CapturingMailSender();

    private final SmtpEmailGateway gateway = new SmtpEmailGateway(sender, "no-reply@cotizaia.test");

    @Test
    void sendsTheMessageWithItsAttachment() throws Exception {
        byte[] pdf = "%PDF-1.7 propuesta".getBytes(StandardCharsets.UTF_8);
        gateway.send(new EmailMessage("duena@agencia.test", "Propuesta enviada: PDF adjunto", "Hola:\nAdjunto va.",
                List.of(new EmailMessage.Attachment("propuesta-7.pdf", "application/pdf", pdf))));

        assertThat(sender.sent).hasSize(1);
        MimeMessage message = sender.sent.get(0);
        assertThat(message.getSubject()).isEqualTo("Propuesta enviada: PDF adjunto");
        assertThat(message.getAllRecipients()).extracting(Object::toString)
                .containsExactly("duena@agencia.test");
        assertThat(message.getFrom()).extracting(Object::toString).containsExactly("no-reply@cotizaia.test");

        Captured captured = Captured.of(message);
        assertThat(captured.text).contains("Adjunto va.");
        assertThat(captured.attachments).hasSize(1);
        Part attachment = captured.attachments.get(0);
        assertThat(attachment.getFileName()).isEqualTo("propuesta-7.pdf");
        assertThat(attachment.getContentType()).contains("application/pdf");
        try (InputStream content = attachment.getInputStream()) {
            assertThat(content.readAllBytes()).isEqualTo(pdf);
        }
    }

    @Test
    void sendsAMessageWithoutAttachmentsAsPlainText() throws Exception {
        gateway.send(new EmailMessage("duena@agencia.test", "Confirma tu correo", "Abre el enlace."));

        MimeMessage message = sender.sent.get(0);
        assertThat(message.getSubject()).isEqualTo("Confirma tu correo");
        Captured captured = Captured.of(message);
        assertThat(captured.text).isEqualTo("Abre el enlace.");
        assertThat(captured.attachments).isEmpty();
    }

    @Test
    void propagatesTransportFailures() {
        sender.fail = true;
        assertThatThrownBy(() -> gateway.send(new EmailMessage("duena@agencia.test", "Asunto", "Cuerpo")))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    void mailPropertiesKeepMailOffAndTheAgreedDefaults() {
        MailProperties properties = new MailProperties();
        assertThat(properties.isEnabled()).as("mail is OFF by default").isFalse();
        assertThat(properties.isVerificationRequired()).isFalse();
        assertThat(properties.isSendToClient()).isFalse();
        assertThat(properties.getPort()).isEqualTo(587);
        assertThat(properties.isStarttls()).isTrue();
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.isLoginVerificationRequired())
                .as("verification can only be enforced when a mailer exists").isFalse();
        properties.setVerificationRequired(true);
        assertThat(properties.isLoginVerificationRequired()).isFalse();
        properties.setEnabled(true);
        assertThat(properties.isLoginVerificationRequired()).isTrue();
    }

    /** Flattens the MIME tree Spring Mail builds: body text plus every named part. */
    record Captured(String text, List<Part> attachments) {

        static Captured of(MimeMessage message) throws Exception {
            StringBuilder text = new StringBuilder();
            List<Part> attachments = new java.util.ArrayList<>();
            collect(message, text, attachments);
            return new Captured(text.toString(), attachments);
        }

        private static void collect(Part part, StringBuilder text, List<Part> attachments) throws Exception {
            Object content = part.getContent();
            if (content instanceof Multipart multipart) {
                for (int index = 0; index < multipart.getCount(); index++) {
                    collect(multipart.getBodyPart(index), text, attachments);
                }
            } else if (part.getFileName() != null) {
                attachments.add(part);
            } else if (content instanceof String body) {
                text.append(body);
            }
        }
    }

    /** Records what would go to SMTP instead of opening a socket. */
    static final class CapturingMailSender extends JavaMailSenderImpl {

        private final List<MimeMessage> sent = new java.util.ArrayList<>();

        private boolean fail;

        @Override
        public void send(MimeMessage message) {
            if (fail) {
                throw new MailSendException("SMTP is down");
            }
            try {
                // A real Transport calls this before writing the wire; without it the MIME
                // headers of a message that never left the JVM keep their defaults.
                message.saveChanges();
            } catch (jakarta.mail.MessagingException exception) {
                throw new IllegalStateException(exception);
            }
            sent.add(message);
        }

        @Override
        public void send(MimeMessage... messages) {
            for (MimeMessage message : messages) {
                send(message);
            }
        }
    }
}
