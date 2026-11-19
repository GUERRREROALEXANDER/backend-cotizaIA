package com.cotizaia.mail;

import java.util.Properties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Chooses the {@link EmailGateway} adapter (spec 16). The switch is {@code mail.enabled}: with it
 * off (the default) the context holds only {@link NoopEmailGateway} and no JavaMailSender exists,
 * so no SMTP configuration is required to boot, run the demo or pass the suite.
 *
 * <p>The transport is built here instead of through {@code spring.mail.*} so the whole contract
 * reads from one namespace ({@code MAIL_*}) and the timeouts stay short: a dead SMTP server must
 * fail quickly rather than hold an HTTP response open.</p>
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfiguration {

    @Bean
    public EmailGateway emailGateway(MailProperties properties, ObjectProvider<JavaMailSender> senders) {
        if (!properties.isEnabled()) {
            return new NoopEmailGateway();
        }
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("mail.enabled=true but no JavaMailSender was configured");
        }
        return new SmtpEmailGateway(sender, properties.getFrom());
    }

    @Bean
    @ConditionalOnProperty(prefix = "mail", name = "enabled", havingValue = "true")
    public JavaMailSender mailSender(MailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setDefaultEncoding("UTF-8");
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            sender.setUsername(properties.getUsername());
            sender.setPassword(properties.getPassword());
        }
        long timeout = properties.getTimeout().toMillis();
        Properties javaMail = sender.getJavaMailProperties();
        javaMail.put("mail.transport.protocol", "smtp");
        javaMail.put("mail.smtp.auth", Boolean.toString(properties.getUsername() != null
                && !properties.getUsername().isBlank()));
        javaMail.put("mail.smtp.starttls.enable", Boolean.toString(properties.isStarttls()));
        javaMail.put("mail.smtp.connectiontimeout", Long.toString(timeout));
        javaMail.put("mail.smtp.timeout", Long.toString(timeout));
        javaMail.put("mail.smtp.writetimeout", Long.toString(timeout));
        return sender;
    }
}
