package com.cotizaia.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * SMTP adapter (spec 16): the only class that touches Spring Mail. Plain text, UTF-8, one part per
 * attachment, so a proposal or contract PDF arrives exactly as the agency downloaded it. Transport
 * failures surface as runtime {@link org.springframework.mail.MailException}s for the caller to
 * swallow.
 */
public class SmtpEmailGateway implements EmailGateway {

    private final JavaMailSender sender;

    private final String from;

    public SmtpEmailGateway(JavaMailSender sender, String from) {
        if (sender == null || from == null || from.isBlank()) {
            throw new IllegalArgumentException("Mail sender and from address are required");
        }
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        MimeMessage mime = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), false);
            for (EmailMessage.Attachment attachment : message.attachments()) {
                helper.addAttachment(attachment.name(),
                        new ByteArrayDataSource(attachment.bytes(), attachment.contentType()));
            }
        } catch (MessagingException exception) {
            throw new MailPreparationException("Cannot build the mail message", exception);
        }
        sender.send(mime);
    }
}
