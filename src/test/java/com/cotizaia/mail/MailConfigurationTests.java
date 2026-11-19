package com.cotizaia.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Spec 16: the adapter choice lives in one place. Mail off means the no-op adapter is what the
 * context hands out and no JavaMailSender is even created; mail on means the SMTP adapter with the
 * configured sender address.
 */
class MailConfigurationTests {

    private final MailConfiguration configuration = new MailConfiguration();

    private final DefaultListableBeanFactory beans = new DefaultListableBeanFactory();

    @Test
    void mailOffWiresTheNoopAdapterAndCreatesNoMailSender() {
        MailProperties properties = new MailProperties();

        EmailGateway gateway = configuration.emailGateway(properties, provider());

        assertThat(gateway).isInstanceOf(NoopEmailGateway.class);
        assertThat(beans.getBeanNamesForType(JavaMailSender.class)).isEmpty();
    }

    @Test
    void mailOnWiresTheSmtpAdapter() {
        MailProperties properties = new MailProperties();
        properties.setEnabled(true);
        properties.setFrom("no-reply@cotizaia.test");
        beans.registerSingleton("mailSender", configuration.mailSender(properties));

        EmailGateway gateway = configuration.emailGateway(properties, provider());

        assertThat(gateway).isInstanceOf(SmtpEmailGateway.class);
        JavaMailSenderImpl sender = beans.getBean(JavaMailSenderImpl.class);
        assertThat(sender.getHost()).isEqualTo(properties.getHost());
        assertThat(sender.getPort()).isEqualTo(properties.getPort());
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("false");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.connectiontimeout")).isEqualTo("5000");
    }

    private ObjectProvider<JavaMailSender> provider() {
        return beans.getBeanProvider(JavaMailSender.class);
    }
}
